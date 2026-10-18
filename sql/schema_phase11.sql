-- =========================================================
-- Phase 11 增量：预警规则维度类型扩展（多维预警）
-- 说明：打通 warning_rule.warning_type 维度链路（实体已补映射，
--       预警生成按规则维度类型落库），并新增心理 / 行为两类
--       预留规则模板（默认停用，不参与现有触发）
-- =========================================================
USE xqfx;

-- 1. 扩展 warning_type 列注释，覆盖心理 / 行为 / 消费 / 综合维度
ALTER TABLE warning_rule
    MODIFY COLUMN warning_type VARCHAR(32) NOT NULL
    COMMENT 'ACADEMIC学业/ATTENDANCE出勤/HOMEWORK作业/FAIL挂科/PROGRESS进度/PSYCH心理/BEHAVIOR行为/CONSUMPTION消费/COMPREHENSIVE综合';

-- 2. 心理关怀预留规则（默认停用；启用后需配置对应心理观测数据源）
INSERT INTO warning_rule (rule_code, rule_name, warning_type, level, condition_json, weight, score_threshold, enabled, description)
SELECT t.* FROM (
    SELECT 'RULE_PSYCH_YELLOW' AS c1, '心理关怀-黄色' AS c2, 'PSYCH' AS c3, 2 AS c4,
           '{"psychRiskIndex":{"gte":70}}' AS c5, 0.80 AS c6, 40 AS c7, 0 AS c8,
           '心理状态观测指数偏高，建议辅导员跟进关怀' AS c9
    UNION ALL SELECT 'RULE_BEHAVIOR_YELLOW', '行为异常-黄色', 'BEHAVIOR', 2,
           '{"behaviorAnomalyCount":{"gte":3}}', 0.75, 40, 0,
           '行为异常次数（缺勤/迟到/违纪）达到阈值'
) t
WHERE NOT EXISTS (SELECT 1 FROM warning_rule r WHERE r.rule_code = t.c1);

-- 3. 校验：展示当前规则维度类型分布
SELECT warning_type, COUNT(*) AS rule_count FROM warning_rule GROUP BY warning_type ORDER BY warning_type;
