-- =========================================================
-- Phase 4 增量：学生标签自动打标规则
-- 规则字段对应 ProfileService#collectMetrics 采集的指标：
--   avg_score / fail_count / attendance_rate / study_minutes
--   video_progress / assignment_submit_rate / risk_probability
-- 操作符支持：gte / gt / lte / lt / eq，多条件需全部满足
-- =========================================================
USE xqfx;

UPDATE tag_definition SET rule_json = '{"risk_probability":{"gte":0.6}}'
WHERE tag_code = 'TAG_HIGH_RISK' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"fail_count":{"gte":1}}'
WHERE tag_code = 'TAG_FAIL_RISK' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"attendance_rate":{"lt":0.8}}'
WHERE tag_code = 'TAG_LOW_ATTEND' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"fail_count":{"gte":1},"avg_score":{"gte":60}}'
WHERE tag_code = 'TAG_PARTIAL' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"avg_score":{"gte":85},"attendance_rate":{"gte":0.9}}'
WHERE tag_code = 'TAG_EXCELLENT' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"study_minutes":{"gte":300}}'
WHERE tag_code = 'TAG_ACTIVE' AND (rule_json IS NULL OR rule_json = '');

UPDATE tag_definition SET rule_json = '{"video_progress":{"lt":50}}'
WHERE tag_code = 'TAG_PROGRESS_LAG' AND (rule_json IS NULL OR rule_json = '');

-- 补充标签配色
UPDATE tag_definition SET color = '#F56C6C' WHERE tag_code = 'TAG_HIGH_RISK' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#E6A23C' WHERE tag_code = 'TAG_FAIL_RISK' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#909399' WHERE tag_code = 'TAG_LOW_ATTEND' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#409EFF' WHERE tag_code = 'TAG_PARTIAL' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#67C23A' WHERE tag_code = 'TAG_EXCELLENT' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#67C23A' WHERE tag_code = 'TAG_ACTIVE' AND (color IS NULL OR color = '');
UPDATE tag_definition SET color = '#B88230' WHERE tag_code = 'TAG_PROGRESS_LAG' AND (color IS NULL OR color = '');
