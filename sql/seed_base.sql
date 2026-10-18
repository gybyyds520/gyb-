-- =============================================================
-- 基础数据初始化（角色、管理员、默认预警规则、默认标签、提示词模板）
-- 执行顺序：先执行 schema.sql，再执行本文件
-- 管理员初始密码：admin123（BCrypt 加密值）
-- =============================================================

USE `xqfx`;

-- ---------- 角色 ----------
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort`) VALUES
(1, 'ADMIN',          '系统管理员', '拥有全部权限', 1),
(2, 'SCHOOL_ADMIN',   '校级管理者', '查看全校学情与报表', 2),
(3, 'COLLEGE_ADMIN',  '院级管理者', '查看本学院学情', 3),
(4, 'COUNSELOR',      '辅导员',     '预警处理与干预帮扶', 4),
(5, 'TEACHER',        '任课教师',   '查看所授课程学情', 5),
(6, 'STUDENT',        '学生',       '查看个人学情', 6)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- ---------- 管理员账号：admin / admin123 ----------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `user_type`, `status`)
VALUES (1, 'admin',
        '$2a$10$ozZNF6MM9FBKwOo7VMDq7u55LGDmY01kJKZgPZN4At2idc4krVL3S',
        '系统管理员', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

-- ---------- 默认预警规则（四级：1蓝 2黄 3橙 4红）----------
INSERT INTO `warning_rule` (`id`, `rule_code`, `rule_name`, `warning_type`, `level`, `condition_json`, `weight`, `score_threshold`, `enabled`, `description`) VALUES
(1, 'RULE_FAIL_RISK_RED',   '挂科风险-红色',   'FAIL',       4, '{"predictedScore":{"lt":50}}',                     1.00, 80, 1, '预测成绩低于50分'),
(2, 'RULE_FAIL_RISK_ORANGE','挂科风险-橙色',   'FAIL',       3, '{"predictedScore":{"lt":60}}',                     1.00, 60, 1, '预测成绩低于60分'),
(3, 'RULE_ATTENDANCE',      '出勤异常-黄色',   'ATTENDANCE', 2, '{"attendanceRate":{"lt":0.85}}',                   0.80, 40, 1, '出勤率低于85%'),
(4, 'RULE_HOMEWORK',        '作业未完成-黄色', 'HOMEWORK',   2, '{"homeworkSubmitRate":{"lt":0.70}}',               0.70, 40, 1, '作业提交率低于70%'),
(5, 'RULE_SCORE_DROP',      '成绩下滑-橙色',   'ACADEMIC',   3, '{"scoreTrend":{"lt":-10}}',                        0.90, 60, 1, '较上一学期下滑超过10分'),
(6, 'RULE_CREDIT_PROGRESS', '学分进度滞后-蓝色','PROGRESS',  1, '{"creditProgressRate":{"lt":0.60}}',               0.60, 20, 1, '学分达成进度低于60%')
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`);

-- ---------- 默认标签定义 ----------
INSERT INTO `tag_definition` (`id`, `tag_code`, `tag_name`, `tag_category`, `color`, `is_auto`, `description`) VALUES
(1,  'TAG_HIGH_RISK',    '高风险学生',   'RISK',        '#f56c6c', 1, '预测风险等级为橙或红'),
(2,  'TAG_FAIL_RISK',    '挂科风险',     'RISK',        '#e6a23c', 1, '存在挂科风险'),
(3,  'TAG_LOW_ATTEND',   '出勤异常',     'BEHAVIOR',    '#e6a23c', 1, '出勤率偏低'),
(4,  'TAG_PARTIAL',      '偏科倾向',     'ACADEMIC',    '#409eff', 1, '存在明显偏科'),
(5,  'TAG_EXCELLENT',    '学业优秀',     'ACADEMIC',    '#67c23a', 1, '平均成绩排名前20%'),
(6,  'TAG_ACTIVE',       '学习活跃',     'BEHAVIOR',    '#67c23a', 1, '在线学习投入度高'),
(7,  'TAG_PROGRESS_LAG', '进度滞后',     'DEVELOPMENT', '#909399', 1, '学分进度低于预期')
ON DUPLICATE KEY UPDATE `tag_name` = VALUES(`tag_name`);

-- ---------- 默认提示词模板 ----------
INSERT INTO `ai_prompt_template` (`id`, `template_code`, `template_name`, `scene`, `content`, `variables_json`, `version`, `enabled`) VALUES
(1, 'TPL_PERSONAL_REPORT', '个人学情报告', 'REPORT',
 '你是高校学业分析助手。请根据以下学生学情数据，生成一份结构化的学情分析报告，包含：1)学业现状概述；2)优势与不足；3)风险点；4)改进建议。\n学生姓名：{name}\n专业班级：{className}\n本学期平均成绩：{avgScore}\n出勤率：{attendanceRate}\n预测成绩：{predictedScore}\n风险等级：{riskLevel}\n特征贡献：{explain}\n请使用客观、书面化的中文表述，控制在600字以内。',
 '["name","className","avgScore","attendanceRate","predictedScore","riskLevel","explain"]', 1, 1),
(2, 'TPL_LEARNING_ADVICE', '个性化学习建议', 'ADVICE',
 '请针对以下学生的学情数据，给出5条具体、可执行的学习改进建议，每条不超过50字。\n学生：{name}，薄弱科目：{weakCourses}，预测风险：{riskLevel}。',
 '["name","weakCourses","riskLevel"]', 1, 1),
(3, 'TPL_PREDICT_EXPLAIN', '预测结果解释', 'EXPLAIN',
 '请用通俗的语言向辅导员解释：为什么该学生被判定为 {riskLevel} 风险。\n主要影响因素：{topFeatures}。\n说明每条因素的含义与影响方向，控制在200字以内。',
 '["riskLevel","topFeatures"]', 1, 1)
ON DUPLICATE KEY UPDATE `template_name` = VALUES(`template_name`);

-- ---------- 系统配置 ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_name`, `remark`) VALUES
('xqfx.ollama.model',   'llama3.2', '默认大模型',     '本地 Ollama 主模型'),
('xqfx.warning.levels', '1,2,3,4',  '启用的预警等级', '蓝/黄/橙/红'),
('xqfx.pass.line',      '60',       '及格线',         '成绩及格分数线')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);
