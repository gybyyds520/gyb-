package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.LearningGoal;
import com.gyb.xqfx.entity.NotifyMessage;
import com.gyb.xqfx.entity.ParentFeedback;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.LearningGoalMapper;
import com.gyb.xqfx.mapper.NotifyMessageMapper;
import com.gyb.xqfx.mapper.ParentFeedbackMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.TodoGroupVO;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 待办中心：按角色聚合待办事项
 *
 * <p>教师/管理者：待处理预警 + 开放工单 + 家长待回执；
 * 学生：我的预警 + 进行中目标 + 未读通知；
 * 家长：子女预警 + 待回执反馈。所有数据按角色数据范围过滤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WarningRecordMapper warningRecordMapper;
    private final InterventionTicketMapper ticketMapper;
    private final ParentFeedbackMapper feedbackMapper;
    private final LearningGoalMapper goalMapper;
    private final NotifyMessageMapper notifyMapper;
    private final StudentMapper studentMapper;
    private final DataScopeService dataScopeService;

    /**
     * 当前用户待办（按角色）
     */
    public List<TodoGroupVO> todos() {
        String userType = SecurityUtils.currentUserType();
        if ("STUDENT".equals(userType)) {
            return studentTodos();
        }
        if ("PARENT".equals(userType)) {
            return parentTodos();
        }
        return staffTodos();
    }

    // ==================== 教师/管理端 ====================

    private List<TodoGroupVO> staffTodos() {
        List<Long> scope = dataScopeService.scopeForQuery();
        LambdaQueryWrapper<WarningRecord> ww = new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStatus, "ACTIVE")
                .orderByDesc(WarningRecord::getCreateTime)
                .last("limit 8");
        if (scope != null) {
            ww.in(WarningRecord::getStudentId, scope);
        }
        List<WarningRecord> warnings = warningRecordMapper.selectList(ww);
        Map<Long, String> names = studentNames(warnings.stream()
                .map(WarningRecord::getStudentId).distinct().toList());

        List<TodoGroupVO.TodoItemVO> warningItems = warnings.stream().map(w -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(w.getId());
            it.setTitle((names.getOrDefault(w.getStudentId(), "学生") + "｜" + levelText(w.getWarningLevel()) + "预警").trim());
            it.setSummary(w.getContent() == null ? "" : w.getContent());
            it.setTime(w.getCreateTime() == null ? "" : w.getCreateTime().format(FMT));
            it.setLevel(levelCode(w.getWarningLevel()));
            it.setStatus(w.getStatus());
            it.setActionPath("/warning");
            return it;
        }).toList();
        List<TodoGroupVO> groups = new ArrayList<>();
        groups.add(group("WARNING", "待处理预警", warningItems));

        // 开放工单
        LambdaQueryWrapper<InterventionTicket> tw = new LambdaQueryWrapper<InterventionTicket>()
                .eq(InterventionTicket::getStatus, "OPEN")
                .orderByDesc(InterventionTicket::getCreateTime)
                .last("limit 8");
        List<InterventionTicket> tickets = ticketMapper.selectList(tw);
        List<TodoGroupVO.TodoItemVO> ticketItems = tickets.stream().map(t -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(t.getId());
            it.setTitle("工单 " + t.getTicketNo() + "（" + t.getInterveneType() + "）");
            it.setSummary(t.getPlan() == null ? "" : t.getPlan());
            it.setTime(t.getCreateTime() == null ? "" : t.getCreateTime().format(FMT));
            it.setLevel(t.getOverdueHours() != null && t.getOverdueHours() > 0 ? "SEVERE" : "NORMAL");
            it.setStatus(t.getStatus());
            it.setActionPath("/intervention");
            return it;
        }).toList();
        groups.add(group("TICKET", "待处理工单", ticketItems));

        // 家长待回执
        LambdaQueryWrapper<ParentFeedback> fw = new LambdaQueryWrapper<ParentFeedback>()
                .eq(ParentFeedback::getAckStatus, "ACKNOWLEDGED")
                .orderByDesc(ParentFeedback::getCreateTime)
                .last("limit 8");
        List<ParentFeedback> feedbacks = feedbackMapper.selectList(fw);
        List<TodoGroupVO.TodoItemVO> feedbackItems = feedbacks.stream().map(f -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(f.getId());
            it.setTitle("家长回执待跟进（学生ID " + f.getStudentId() + "）");
            it.setSummary(f.getContent() == null ? "" : f.getContent());
            it.setTime(f.getCreateTime() == null ? "" : f.getCreateTime().format(FMT));
            it.setLevel("NORMAL");
            it.setStatus(f.getAckStatus());
            it.setActionPath("/parent");
            return it;
        }).toList();
        groups.add(group("FEEDBACK", "家长回执跟进", feedbackItems));
        return groups.stream().filter(g -> !g.getItems().isEmpty()).toList();
    }

    // ==================== 学生端 ====================

    private List<TodoGroupVO> studentTodos() {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            return List.of();
        }
        List<TodoGroupVO> groups = new ArrayList<>();

        // 我的预警
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getStudentId, studentId)
                        .eq(WarningRecord::getStatus, "ACTIVE")
                        .orderByDesc(WarningRecord::getCreateTime)
                        .last("limit 8"));
        List<TodoGroupVO.TodoItemVO> warningItems = warnings.stream().map(w -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(w.getId());
            it.setTitle(levelText(w.getWarningLevel()) + "学业预警");
            it.setSummary(w.getContent() == null ? "" : w.getContent());
            it.setTime(w.getCreateTime() == null ? "" : w.getCreateTime().format(FMT));
            it.setLevel(levelCode(w.getWarningLevel()));
            it.setStatus(w.getStatus());
            it.setActionPath("/my");
            return it;
        }).toList();
        groups.add(group("WARNING", "我的预警", warningItems));

        // 进行中目标
        List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getStudentId, studentId)
                .eq(LearningGoal::getStatus, "ACTIVE")
                .orderByAsc(LearningGoal::getDeadline)
                .last("limit 8"));
        List<TodoGroupVO.TodoItemVO> goalItems = goals.stream().map(g -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(g.getId());
            it.setTitle("学习目标：" + goalTypeText(g.getGoalType()) + " " + g.getTargetValue());
            it.setSummary(g.getDeadline() == null ? "未设截止日期" : "截止 " + g.getDeadline());
            it.setTime(g.getDeadline() == null ? "" : g.getDeadline() + " 23:59");
            it.setLevel("NORMAL");
            it.setStatus(g.getStatus());
            it.setActionPath("/goal");
            return it;
        }).toList();
        groups.add(group("GOAL", "进行中目标", goalItems));

        // 未读通知
        List<NotifyMessage> unread = notifyMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, SecurityUtils.currentUserIdOrNull())
                .eq(NotifyMessage::getIsRead, 0)
                .orderByDesc(NotifyMessage::getCreateTime)
                .last("limit 8"));
        List<TodoGroupVO.TodoItemVO> notifyItems = unread.stream().map(n -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(n.getId());
            it.setTitle(n.getTitle() == null ? "新通知" : n.getTitle());
            it.setSummary(n.getContent() == null ? "" : n.getContent());
            it.setTime(n.getCreateTime() == null ? "" : n.getCreateTime().format(FMT));
            it.setLevel("NORMAL");
            it.setStatus("UNREAD");
            it.setActionPath("/notify");
            return it;
        }).toList();
        groups.add(group("NOTIFY", "未读通知", notifyItems));
        return groups.stream().filter(g -> !g.getItems().isEmpty()).toList();
    }

    // ==================== 家长端 ====================

    private List<TodoGroupVO> parentTodos() {
        Long userId = SecurityUtils.currentUserId();
        List<Long> children = dataScopeService.childStudentIds(userId);
        if (children.isEmpty()) {
            return List.of();
        }
        List<TodoGroupVO> groups = new ArrayList<>();
        Map<Long, String> names = studentNames(children);

        // 子女预警
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .in(WarningRecord::getStudentId, children)
                        .eq(WarningRecord::getStatus, "ACTIVE")
                        .orderByDesc(WarningRecord::getCreateTime)
                        .last("limit 8"));
        List<TodoGroupVO.TodoItemVO> warningItems = warnings.stream().map(w -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(w.getId());
            it.setTitle("子女 " + names.getOrDefault(w.getStudentId(), "学生") + " ｜" + levelText(w.getWarningLevel()) + "预警");
            it.setSummary(w.getContent() == null ? "" : w.getContent());
            it.setTime(w.getCreateTime() == null ? "" : w.getCreateTime().format(FMT));
            it.setLevel(levelCode(w.getWarningLevel()));
            it.setStatus(w.getStatus());
            it.setActionPath("/parent");
            return it;
        }).toList();
        groups.add(group("WARNING", "子女预警", warningItems));

        // 待回执反馈
        List<ParentFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<ParentFeedback>()
                        .in(ParentFeedback::getStudentId, children)
                        .eq(ParentFeedback::getParentId, userId)
                        .orderByDesc(ParentFeedback::getCreateTime)
                        .last("limit 8"));
        List<TodoGroupVO.TodoItemVO> feedbackItems = feedbacks.stream().map(f -> {
            TodoGroupVO.TodoItemVO it = new TodoGroupVO.TodoItemVO();
            it.setId(f.getId());
            it.setTitle("预警/帮扶回执待确认");
            it.setSummary(f.getContent() == null ? "" : f.getContent());
            it.setTime(f.getCreateTime() == null ? "" : f.getCreateTime().format(FMT));
            it.setLevel("NORMAL");
            it.setStatus(f.getAckStatus());
            it.setActionPath("/parent");
            return it;
        }).toList();
        groups.add(group("FEEDBACK", "回执与反馈", feedbackItems));
        return groups.stream().filter(g -> !g.getItems().isEmpty()).toList();
    }

    // ==================== 工具 ====================

    private Map<Long, String> studentNames(List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
    }

    private TodoGroupVO group(String code, String title, List<TodoGroupVO.TodoItemVO> items) {
        TodoGroupVO g = new TodoGroupVO();
        g.setCode(code);
        g.setTitle(title);
        g.setItems(items);
        g.setCount(items.size());
        return g;
    }

    private String levelText(Integer level) {
        return switch (level == null ? 0 : level) {
            case 4 -> "红色";
            case 3 -> "橙色";
            case 2 -> "黄色";
            case 1 -> "蓝色";
            default -> "";
        };
    }

    private String levelCode(Integer level) {
        return switch (level == null ? 0 : level) {
            case 4 -> "SEVERE";
            case 3 -> "ATTENTION";
            default -> "NORMAL";
        };
    }

    private String goalTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "AVG_SCORE" -> "平均分";
            case "GPA" -> "绩点";
            case "COURSE" -> "单科";
            default -> type;
        };
    }
}
