package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.PaperScan;
import com.gyb.xqfx.entity.QuestionBank;
import com.gyb.xqfx.mapper.PaperScanMapper;
import com.gyb.xqfx.mapper.QuestionBankMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 试卷外部录入（图片/PDF/Word）→ AI 识别 → 试卷分析 → 题库
 *
 * <p>链路：上传文件落盘 → 本地 Python 通道提取文本（PDF 文字版直取，
 * 扫描版/图片走 PaddleOCR）→ 本地大模型结构化解析题目 → 生成试卷分析 →
 * 题目入题库（question_bank），题库可编辑检索，并一键复用为作业。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperScanService {

    private static final List<String> ALLOWED_EXT = List.of(".png", ".jpg", ".jpeg", ".bmp", ".webp", ".pdf", ".docx", ".doc");

    private final PaperScanMapper paperScanMapper;
    private final QuestionBankMapper questionBankMapper;
    private final HomeworkService homeworkService;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    @Value("${xqfx.scan.upload-dir:./upload/scan}")
    private String uploadDir;

    @Value("${xqfx.scan.python-path:./.ocr_venv/Scripts/python.exe}")
    private String pythonPath;

    @Value("${xqfx.scan.script-path:./scripts/paper_extract.py}")
    private String scriptPath;

    /** 题库题目 DTO（AI 解析结果） */
    public static class BankQuestion {
        public String no;
        public String type;
        public String stem;
        public List<String> options;
        public String answer;
        public String analysis;
        public BigDecimal score;
        public String knowledgePoint;
    }

    /** 试卷分析 DTO */
    public static class PaperAnalysis {
        public BigDecimal difficulty;
        public String summary;
        public List<String> knowledgePoints;
        public Map<String, Integer> typeStats;
    }

    /** 上传并提取文本 */
    public PaperScan upload(MultipartFile file, String title, Long courseId, String courseName, Long creatorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的试卷文件");
        }
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的文件类型：" + ext + "，支持图片(png/jpg/bmp/webp)、PDF、Word(docx/doc)");
        }
        String fileType = ".pdf".equals(ext) ? "PDF" : (".docx".equals(ext) || ".doc".equals(ext) ? "WORD" : "IMAGE");
        String scanTitle = (title == null || title.isBlank())
                ? original.substring(0, Math.min(original.lastIndexOf('.'), 120))
                : title.trim();

        File dir = new File(uploadDir, LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建上传目录失败");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.toPath().resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败：" + e.getMessage());
        }

        PaperScan scan = new PaperScan();
        scan.setTitle(scanTitle);
        scan.setCourseId(courseId);
        scan.setCourseName(courseName);
        scan.setFileName(original);
        scan.setFileType(fileType);
        scan.setFilePath(target.toString());
        scan.setStatus("PENDING");
        scan.setQuestionCount(0);
        scan.setCreatorId(creatorId);
        scan.setIsDeleted(0);
        paperScanMapper.insert(scan);

        // 同步提取文本（首次 OCR 模型加载约 30~60s）
        Map<String, Object> extracted = callExtract(target.toString());
        boolean ok = Boolean.TRUE.equals(extracted.get("ok"));
        if (ok) {
            scan.setOcrText((String) extracted.getOrDefault("text", ""));
            scan.setMethod((String) extracted.getOrDefault("method", ""));
            scan.setStatus("PARSED");
        } else {
            scan.setStatus("FAILED");
            scan.setErrorMsg(String.valueOf(extracted.getOrDefault("error", "提取失败")));
        }
        paperScanMapper.updateById(scan);
        log.info("试卷上传完成: id={}, title={}, method={}, status={}", scan.getId(), scanTitle, scan.getMethod(), scan.getStatus());
        return scan;
    }

    /** 调用本地 Python 通道提取文本 */
    private Map<String, Object> callExtract(String filePath) {
        String python = resolveBasePath(pythonPath);
        String script = resolveBasePath(scriptPath);
        String outJson = filePath + ".extract.json";
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(python, script, filePath, outJson);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(180, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                result.put("ok", false);
                result.put("error", "文本提取超时（>180s）");
                return result;
            }
            File outFile = new File(outJson);
            if (!outFile.exists()) {
                result.put("ok", false);
                result.put("error", "提取进程未产出结果");
                return result;
            }
            JsonNode node = objectMapper.readTree(outFile);
            result.put("ok", node.path("ok").asBoolean(false));
            result.put("text", node.path("text").asText(""));
            result.put("method", node.path("method").asText(""));
            result.put("error", node.path("error").asText(""));
            outFile.delete();
            return result;
        } catch (Exception e) {
            log.warn("文本提取调用失败: {}", e.getMessage());
            result.put("ok", false);
            result.put("error", "调用提取通道失败：" + e.getMessage());
            return result;
        }
    }

    private String resolveBasePath(String p) {
        if (new File(p).isAbsolute()) {
            return p;
        }
        return new File(".").getAbsolutePath() + File.separator + p;
    }

    /** AI 解析题目 + 生成试卷分析（幂等：重解析先清旧题） */
    public PaperScan parseAndAnalyze(Long scanId) {
        PaperScan scan = requireScan(scanId);
        if (scan.getOcrText() == null || scan.getOcrText().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该试卷没有可解析的文本，请检查提取是否成功");
        }
        // 清旧题（重解析）
        questionBankMapper.delete(new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getScanId, scanId));

        List<BankQuestion> questions = aiParseQuestions(scan.getOcrText());
        int saved = 0;
        for (BankQuestion q : questions) {
            if (q.stem == null || q.stem.isBlank()) {
                continue;
            }
            QuestionBank row = new QuestionBank();
            row.setScanId(scanId);
            row.setCourseId(scan.getCourseId() == null ? null : scan.getCourseId());
            row.setQuestionNo(q.no);
            row.setQuestionType(normalizeType(q.type));
            row.setStem(q.stem.trim());
            row.setOptionsJson(q.options == null || q.options.isEmpty() ? "[]" : toJson(q.options));
            row.setAnswer(q.answer);
            row.setAnalysis(q.analysis);
            row.setScore(q.score);
            row.setKnowledgePoint(q.knowledgePoint);
            row.setDifficulty("中");
            row.setStatus("ACTIVE");
            row.setIsDeleted(0);
            questionBankMapper.insert(row);
            saved++;
        }
        if (saved == 0) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 未能解析出有效题目，请检查试卷文本质量后重试");
        }
        PaperAnalysis analysis = aiAnalyze(scan.getOcrText(), questions);
        scan.setQuestionCount(saved);
        scan.setAiSummary(analysis == null ? null : analysis.summary);
        scan.setDifficulty(analysis == null ? null : analysis.difficulty);
        scan.setStatus("REVIEWED");
        paperScanMapper.updateById(scan);
        log.info("试卷解析完成: id={}, questions={}", scanId, saved);
        return scan;
    }

    /** AI 结构化解析题目 */
    private List<BankQuestion> aiParseQuestions(String text) {
        String system = """
                你是试卷结构化解析引擎。请把试卷文本解析为 JSON 数组，数组每项包含字段：
                {"no":"题号","type":"题型","stem":"题干","options":["A. xxx","B. xxx"],"answer":"参考答案","analysis":"试题解析","score":分值,"knowledgePoint":"知识点"}
                题型取值：SINGLE(单选)/MULTI(多选)/FILL(填空)/JUDGE(判断)/ESSAY(简答作文)。
                没有选项的题 options 给空数组 []。无法识别分值给 0。
                只输出 JSON 数组本身，不要输出任何其它文字或解释。""";
        String user = "试卷文本如下：\n" + truncate(text, 18000);
        OllamaClient.ChatResult res = ollamaClient.chatWithModel("llama3.1:8b", system, user);
        if (res == null || res.content == null || res.content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "大模型解析失败（服务不可用），请稍后重试");
        }
        try {
            String json = extractJsonArray(res.content);
            return objectMapper.readValue(json, new TypeReference<List<BankQuestion>>() {});
        } catch (Exception e) {
            log.warn("题目解析 JSON 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "大模型输出无法解析为题目，请重试或改用文字版文档");
        }
    }

    /** AI 试卷分析 */
    private PaperAnalysis aiAnalyze(String text, List<BankQuestion> questions) {
        int total = questions.size();
        long essay = questions.stream().filter(q -> "ESSAY".equals(normalizeType(q.type))).count();
        String system = """
                你是试卷分析专家。请对试卷给出简短分析，只输出 JSON 对象：
                {"difficulty": 0到1之间的小数,"summary":"一段不超过200字的总体点评","knowledgePoints":["知识点1","知识点2"]}
                只输出 JSON 本身。""";
        String user = "试卷信息：共" + total + "题，其中主观题" + essay + "道。知识点提取与难度评估请依据下列文本：\n"
                + truncate(text, 12000);
        try {
            OllamaClient.ChatResult res = ollamaClient.chatWithModel("llama3.1:8b", system, user);
            if (res == null || res.content == null || res.content.isBlank()) {
                return null;
            }
            String json = extractJsonObject(res.content);
            JsonNode node = objectMapper.readTree(json);
            PaperAnalysis a = new PaperAnalysis();
            a.difficulty = node.has("difficulty") ? BigDecimal.valueOf(node.get("difficulty").asDouble()) : null;
            a.summary = node.path("summary").asText(null);
            return a;
        } catch (Exception e) {
            log.warn("试卷分析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 中文题型 → 枚举 */
    private String normalizeType(String t) {
        if (t == null) {
            return "SINGLE";
        }
        String s = t.trim();
        if (s.contains("多选")) {
            return "MULTI";
        }
        if (s.contains("填空")) {
            return "FILL";
        }
        if (s.contains("判断")) {
            return "JUDGE";
        }
        if (s.contains("简答") || s.contains("作文") || s.contains("问答") || s.contains("论述") || s.contains("解答")) {
            return "ESSAY";
        }
        if (s.contains("单选") || s.startsWith("SINGLE")) {
            return "SINGLE";
        }
        if (s.startsWith("MULTI")) {
            return "MULTI";
        }
        if (s.startsWith("FILL")) {
            return "FILL";
        }
        if (s.startsWith("JUDGE")) {
            return "JUDGE";
        }
        if (s.startsWith("ESSAY")) {
            return "ESSAY";
        }
        return "SINGLE";
    }

    private String extractJsonArray(String content) {
        int s = content.indexOf('[');
        int e = content.lastIndexOf(']');
        if (s < 0 || e <= s) {
            throw new IllegalStateException("no json array");
        }
        return content.substring(s, e + 1);
    }

    private String extractJsonObject(String content) {
        int s = content.indexOf('{');
        int e = content.lastIndexOf('}');
        if (s < 0 || e <= s) {
            throw new IllegalStateException("no json object");
        }
        return content.substring(s, e + 1);
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 录入列表 */
    public List<Map<String, Object>> listScans(String keyword, String status) {
        LambdaQueryWrapper<PaperScan> qw = new LambdaQueryWrapper<PaperScan>()
                .like(keyword != null && !keyword.isBlank(), PaperScan::getTitle, keyword)
                .eq(status != null && !status.isBlank(), PaperScan::getStatus, status)
                .orderByDesc(PaperScan::getCreateTime);
        List<PaperScan> list = paperScanMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PaperScan s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("fileName", s.getFileName());
            m.put("fileType", s.getFileType());
            m.put("method", s.getMethod());
            m.put("status", s.getStatus());
            m.put("questionCount", s.getQuestionCount());
            m.put("difficulty", s.getDifficulty());
            m.put("errorMsg", s.getErrorMsg());
            m.put("createTime", s.getCreateTime());
            m.put("textPreview", s.getOcrText() == null ? "" : truncate(s.getOcrText(), 200));
            result.add(m);
        }
        return result;
    }

    /** 录入详情 + 题目 */
    public Map<String, Object> detail(Long scanId) {
        PaperScan scan = requireScan(scanId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", scan.getId());
        m.put("title", scan.getTitle());
        m.put("fileName", scan.getFileName());
        m.put("fileType", scan.getFileType());
        m.put("method", scan.getMethod());
        m.put("status", scan.getStatus());
        m.put("ocrText", scan.getOcrText());
        m.put("aiSummary", scan.getAiSummary());
        m.put("difficulty", scan.getDifficulty());
        m.put("questionCount", scan.getQuestionCount());
        m.put("createTime", scan.getCreateTime());
        m.put("questions", bankListOfScan(scanId));
        return m;
    }

    /** 指定批次的题目 */
    public List<Map<String, Object>> bankListOfScan(Long scanId) {
        List<QuestionBank> list = questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getScanId, scanId)
                .orderByAsc(QuestionBank::getQuestionNo));
        return toBankRows(list);
    }

    /** 题库列表（跨批次检索） */
    public List<Map<String, Object>> bankList(Long courseId, String knowledgePoint, String type, String keyword) {
        LambdaQueryWrapper<QuestionBank> qw = new LambdaQueryWrapper<QuestionBank>()
                .eq(courseId != null, QuestionBank::getCourseId, courseId)
                .like(knowledgePoint != null && !knowledgePoint.isBlank(), QuestionBank::getKnowledgePoint, knowledgePoint)
                .eq(type != null && !type.isBlank(), QuestionBank::getQuestionType, type)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(QuestionBank::getStem, keyword)
                                .or().like(QuestionBank::getKnowledgePoint, keyword))
                .orderByDesc(QuestionBank::getCreateTime);
        return toBankRows(questionBankMapper.selectList(qw));
    }

    private List<Map<String, Object>> toBankRows(List<QuestionBank> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestionBank q : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("scanId", q.getScanId());
            m.put("questionNo", q.getQuestionNo());
            m.put("questionType", q.getQuestionType());
            m.put("stem", q.getStem());
            m.put("optionsJson", q.getOptionsJson());
            m.put("answer", q.getAnswer());
            m.put("analysis", q.getAnalysis());
            m.put("score", q.getScore());
            m.put("knowledgePoint", q.getKnowledgePoint());
            m.put("difficulty", q.getDifficulty());
            m.put("createTime", q.getCreateTime());
            rows.add(m);
        }
        return rows;
    }

    /** 编辑题库题目 */
    public void bankEdit(Long id, String stem, String answer, String analysis, BigDecimal score,
                         String knowledgePoint, String difficulty, String questionType, String optionsJson) {
        QuestionBank q = questionBankMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        if (stem != null) {
            q.setStem(stem);
        }
        if (answer != null) {
            q.setAnswer(answer);
        }
        if (analysis != null) {
            q.setAnalysis(analysis);
        }
        if (score != null) {
            q.setScore(score);
        }
        if (knowledgePoint != null) {
            q.setKnowledgePoint(knowledgePoint);
        }
        if (difficulty != null) {
            q.setDifficulty(difficulty);
        }
        if (questionType != null) {
            q.setQuestionType(questionType);
        }
        if (optionsJson != null) {
            q.setOptionsJson(optionsJson);
        }
        questionBankMapper.updateById(q);
    }

    /** 删除题库题目 */
    public void bankDelete(Long id) {
        QuestionBank q = questionBankMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        questionBankMapper.deleteById(id);
    }

    /** 删除录入批次（级联删题） */
    public void deleteScan(Long scanId, Long userId) {
        PaperScan scan = requireScan(scanId);
        questionBankMapper.delete(new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getScanId, scanId));
        paperScanMapper.deleteById(scanId);
        try {
            Files.deleteIfExists(Path.of(scan.getFilePath()));
            Files.deleteIfExists(Path.of(scan.getFilePath() + ".extract.json"));
        } catch (Exception ignore) {
            // 文件清理失败不影响主流程
        }
    }

    /** 题库题目 → 一键布置作业 */
    public Long toHomework(Long creatorId, List<Long> bankIds, String title, Long courseId, String courseName,
                           Long clazzId, LocalDateTime deadline) {
        if (bankIds == null || bankIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要加入作业的题目");
        }
        List<QuestionBank> rows = questionBankMapper.selectBatchIds(bankIds);
        List<HomeworkService.Question> questions = new ArrayList<>();
        for (QuestionBank b : rows) {
            HomeworkService.Question q = new HomeworkService.Question();
            q.no = b.getQuestionNo();
            q.type = mapToHomeworkType(b.getQuestionType());
            q.content = buildContent(b);
            q.answer = b.getAnswer();
            q.score = b.getScore() == null ? BigDecimal.ZERO : b.getScore();
            q.knowledgePoint = b.getKnowledgePoint();
            questions.add(q);
        }
        return homeworkService.create(creatorId, title, courseId, courseName, clazzId, questions, deadline);
    }

    private String mapToHomeworkType(String t) {
        if (t == null) {
            return "ESSAY";
        }
        return switch (t) {
            case "FILL" -> "FILL";
            case "ESSAY" -> "ESSAY";
            default -> "CHOICE"; // SINGLE/MULTI/JUDGE 以选择题形式入作业
        };
    }

    private String buildContent(QuestionBank b) {
        StringBuilder sb = new StringBuilder(b.getStem() == null ? "" : b.getStem());
        if (b.getOptionsJson() != null && !b.getOptionsJson().isBlank() && !"[]".equals(b.getOptionsJson())) {
            try {
                List<String> opts = objectMapper.readValue(b.getOptionsJson(), new TypeReference<List<String>>() {});
                for (String o : opts) {
                    sb.append("\n").append(o);
                }
            } catch (Exception ignore) {
                // 选项解析失败则仅题干
            }
        }
        return sb.toString();
    }

    private PaperScan requireScan(Long scanId) {
        PaperScan scan = paperScanMapper.selectById(scanId);
        if (scan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷记录不存在");
        }
        return scan;
    }
}
