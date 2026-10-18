package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gyb.xqfx.annotation.Audit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.MlModel;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.client.MlServiceClient;
import com.gyb.xqfx.mapper.MlModelMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.vo.PredictionVO;
import com.gyb.xqfx.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 学业预测
 */
@Tag(name = "学情预测")
@RestController
@RequestMapping("/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionResultMapper predictionResultMapper;
    private final MlModelMapper mlModelMapper;
    private final StudentMapper studentMapper;
    private final TermMapper termMapper;
    private final MlServiceClient mlServiceClient;
    private final com.gyb.xqfx.service.DataScopeService dataScopeService;

    @Operation(summary = "预测服务健康检查")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    @GetMapping("/health")
    public Result<Boolean> health() {
        return Result.success(mlServiceClient.isAvailable() ? Boolean.TRUE : Boolean.FALSE,
                mlServiceClient.isAvailable() ? "预测服务可用" : "预测服务不可用");
    }

    @Operation(summary = "训练模型")
    @PostMapping("/train")
    @RequireRole({"ADMIN"})
    public Result<MlModel> train(@RequestParam(defaultValue = "random_forest") String modelType) {
        return Result.success(predictionService.train(modelType), "模型训练完成");
    }

    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Operation(summary = "执行批量预测")
    @PostMapping("/run")
    @Audit(module = "学业预测", operation = "执行批量预测")
    public Result<Map<String, Object>> run(@RequestParam Long termId,
                                           @RequestParam(defaultValue = "random_forest") String modelType) {
        int count = predictionService.predict(termId, modelType);
        return Result.success(Map.of("termId", termId, "count", count), "预测完成");
    }

    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Operation(summary = "预测结果分页查询")
    @GetMapping("/result/page")
    public Result<PageResult<PredictionVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Integer riskLevel) {
        LambdaQueryWrapper<PredictionResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(termId != null, PredictionResult::getTermId, termId);
        wrapper.eq(riskLevel != null, PredictionResult::getRiskLevel, riskLevel);
        // 数据范围：限定可见学生
        java.util.List<Long> scope = dataScopeService.scopeForQuery();
        wrapper.in(scope != null && !scope.isEmpty(), PredictionResult::getStudentId, scope);
        wrapper.orderByDesc(PredictionResult::getRiskLevel).orderByDesc(PredictionResult::getRiskProbability);

        Page<PredictionResult> page = predictionResultMapper.selectPage(new Page<>(current, size), wrapper);

        Map<Long, Student> students = toEntityMap(page.getRecords(), PredictionResult::getStudentId,
                studentMapper, Student::getId);
        Map<Long, Term> terms = toEntityMap(page.getRecords(), PredictionResult::getTermId,
                termMapper, Term::getId);

        List<PredictionVO> vos = page.getRecords().stream().map(p -> {
            PredictionVO vo = new PredictionVO();
            BeanUtils.copyProperties(p, vo);
            Student s = students.get(p.getStudentId());
            if (s != null) {
                vo.setStudentNo(s.getStudentNo());
                vo.setStudentName(s.getName());
            }
            Term t = terms.get(p.getTermId());
            if (t != null) {
                vo.setTermName(t.getTermName());
            }
            return vo;
        }).toList();

        Page<PredictionVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return Result.success(PageResult.of(voPage));
    }

    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Operation(summary = "已训练模型列表")
    @GetMapping("/models")
    public Result<List<MlModel>> models() {
        return Result.success(mlModelMapper.selectList(
                new LambdaQueryWrapper<MlModel>().orderByDesc(MlModel::getId)));
    }

    private <E> Map<Long, E> toEntityMap(List<PredictionResult> records,
                                         Function<PredictionResult, Long> idGetter,
                                         com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper,
                                         Function<E, Long> entityIdGetter) {
        List<Long> ids = records.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(entityIdGetter, Function.identity(), (a, b) -> a));
    }
}
