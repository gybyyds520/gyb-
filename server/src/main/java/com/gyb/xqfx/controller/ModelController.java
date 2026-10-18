package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.MlModel;
import com.gyb.xqfx.mapper.MlModelMapper;
import com.gyb.xqfx.model.vo.ModelCompareVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型管理与评估中心
 */
@Tag(name = "模型管理")
@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
@Slf4j
public class ModelController {

    private final MlModelMapper mlModelMapper;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> METRIC_NAMES = Map.of(
            "accuracy", "准确率", "precision", "精确率", "recall", "召回率",
            "f1", "F1 值", "auc", "AUC", "rmse", "RMSE");

    @Operation(summary = "模型分页列表")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @GetMapping("/list")
    public Result<PageResult<MlModel>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String algorithm,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<MlModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(algorithm != null && !algorithm.isBlank(), MlModel::getAlgorithm, algorithm);
        wrapper.eq(status != null && !status.isBlank(), MlModel::getStatus, status);
        wrapper.orderByDesc(MlModel::getId);
        IPage<MlModel> page = mlModelMapper.selectPage(new Page<>(current, size), wrapper);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "模型详情（含指标与特征重要性）")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        MlModel model = mlModelMapper.selectById(id);
        if (model == null) {
            return Result.fail("模型不存在");
        }
        return Result.success(Map.of(
                "model", model,
                "metrics", parseMap(model.getMetricsJson()),
                "features", parseList(model.getFeatureListJson())));
    }

    @Operation(summary = "多模型指标对比")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @GetMapping("/compare")
    public Result<List<ModelCompareVO>> compare() {
        List<MlModel> models = mlModelMapper.selectList(
                new LambdaQueryWrapper<MlModel>().orderByDesc(MlModel::getId));
        List<ModelCompareVO> vos = models.stream().map(m -> {
            ModelCompareVO vo = new ModelCompareVO();
            vo.setModelId(m.getId());
            vo.setModelCode(m.getModelCode());
            vo.setModelName(m.getModelName());
            vo.setAlgorithm(m.getAlgorithm());
            vo.setVersion(m.getVersion());
            vo.setStatus(m.getStatus());
            vo.setTrainTime(m.getTrainTime());
            vo.setTrainSampleCount(m.getTrainSampleCount());
            vo.setMetrics(parseMap(m.getMetricsJson()));
            return vo;
        }).toList();
        return Result.success(vos);
    }

    @Operation(summary = "模型上线/下线")
    @Audit(module = "模型管理", operation = "切换模型部署状态")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN"})
    @PostMapping("/{id}/status")
    public Result<Boolean> status(@PathVariable Long id, @RequestParam String status) {
        if (!List.of("DEPLOYED", "DISABLED", "TRAINED").contains(status)) {
            return Result.fail("状态仅支持 DEPLOYED / DISABLED / TRAINED");
        }
        MlModel model = mlModelMapper.selectById(id);
        if (model == null) {
            return Result.fail("模型不存在");
        }
        model.setStatus(status);
        return Result.success(mlModelMapper.updateById(model) > 0, "模型状态已更新");
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("模型指标 JSON 解析失败：{}", e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("模型特征 JSON 解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    public static Map<String, String> metricNames() {
        return METRIC_NAMES;
    }
}
