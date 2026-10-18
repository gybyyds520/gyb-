-- =============================================================
-- 基于 Ollama 大模型的高校学情预测分析系统 —— 数据库结构
-- MySQL 8.0 / utf8mb4
-- 设计日期：2026-10-17
-- 命名规范：snake_case；每张表含 id / create_time / update_time / is_deleted
-- =============================================================

CREATE DATABASE IF NOT EXISTS `xqfx`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `xqfx`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- 一、系统与安全（6 张）
-- =============================================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  `password`        VARCHAR(128) NOT NULL                COMMENT '密码(BCrypt)',
  `real_name`       VARCHAR(64)  DEFAULT NULL            COMMENT '姓名',
  `user_type`       VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '类型: STUDENT学生/TEACHER教师/COUNSELOR辅导员/COLLEGE_ADMIN院级/SCHOOL_ADMIN校级/ADMIN超管',
  `phone`           VARCHAR(32)  DEFAULT NULL            COMMENT '手机号',
  `email`           VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
  `avatar`          VARCHAR(255) DEFAULT NULL            COMMENT '头像URL',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态: 1启用 0禁用',
  `last_login_time` DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  `last_login_ip`   VARCHAR(64)  DEFAULT NULL            COMMENT '最后登录IP',
  `create_by`       BIGINT       DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       BIGINT       DEFAULT NULL,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_type` (`user_type`)
) ENGINE = InnoDB COMMENT = '系统用户表';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码',
  `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL,
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      TINYINT     NOT NULL DEFAULT 1,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE = InnoDB COMMENT = '角色表';

-- 3. 用户-角色关联
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB COMMENT = '用户角色关联表';

-- 4. 菜单/权限表
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `parent_id`      BIGINT      NOT NULL DEFAULT 0 COMMENT '父级ID',
  `menu_name`      VARCHAR(64) NOT NULL COMMENT '菜单名称',
  `menu_code`      VARCHAR(64) DEFAULT NULL COMMENT '菜单编码',
  `menu_type`      VARCHAR(20) NOT NULL DEFAULT 'MENU' COMMENT 'DIR目录/MENU菜单/BUTTON按钮',
  `path`           VARCHAR(255) DEFAULT NULL COMMENT '路由路径',
  `component`      VARCHAR(255) DEFAULT NULL COMMENT '前端组件',
  `icon`           VARCHAR(64) DEFAULT NULL,
  `permission`     VARCHAR(128) DEFAULT NULL COMMENT '权限标识',
  `sort`           INT         NOT NULL DEFAULT 0,
  `visible`        TINYINT     NOT NULL DEFAULT 1,
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent` (`parent_id`)
) ENGINE = InnoDB COMMENT = '菜单权限表';

-- 5. 角色-菜单关联
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `menu_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE = InnoDB COMMENT = '角色菜单关联表';

-- 6. 系统配置
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`  VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` TEXT                 COMMENT '配置值',
  `config_name` VARCHAR(128) DEFAULT NULL COMMENT '配置名称',
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB COMMENT = '系统配置表';

-- =============================================================
-- 二、组织与基础数据（6 张）
-- =============================================================

-- 7. 学院
CREATE TABLE IF NOT EXISTS `college` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `college_code` VARCHAR(32) NOT NULL COMMENT '学院代码',
  `college_name` VARCHAR(64) NOT NULL COMMENT '学院名称',
  `dean`         VARCHAR(64) DEFAULT NULL COMMENT '负责人',
  `phone`        VARCHAR(32) DEFAULT NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_college_code` (`college_code`)
) ENGINE = InnoDB COMMENT = '学院表';

-- 8. 专业
CREATE TABLE IF NOT EXISTS `major` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `college_id`  BIGINT      NOT NULL COMMENT '所属学院',
  `major_code`  VARCHAR(32) NOT NULL COMMENT '专业代码',
  `major_name`  VARCHAR(64) NOT NULL COMMENT '专业名称',
  `degree_type` VARCHAR(20) DEFAULT NULL COMMENT '学位类型',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_code` (`major_code`),
  KEY `idx_major_college` (`college_id`)
) ENGINE = InnoDB COMMENT = '专业表';

-- 9. 班级
CREATE TABLE IF NOT EXISTS `clazz` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `major_id`      BIGINT      NOT NULL,
  `clazz_code`    VARCHAR(32) NOT NULL COMMENT '班级代码',
  `clazz_name`    VARCHAR(64) NOT NULL COMMENT '班级名称',
  `grade`         VARCHAR(16) DEFAULT NULL COMMENT '年级',
  `head_teacher_id` BIGINT    DEFAULT NULL COMMENT '班主任(教师ID)',
  `counselor_id`  BIGINT      DEFAULT NULL COMMENT '辅导员(教师ID)',
  `student_count` INT         NOT NULL DEFAULT 0,
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clazz_code` (`clazz_code`),
  KEY `idx_clazz_major` (`major_id`)
) ENGINE = InnoDB COMMENT = '班级表';

-- 10. 学期
CREATE TABLE IF NOT EXISTS `term` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `term_code`  VARCHAR(32) NOT NULL COMMENT '学期代码 如 2026-2027-1',
  `term_name`  VARCHAR(64) NOT NULL COMMENT '学期名称',
  `start_date` DATE        DEFAULT NULL,
  `end_date`   DATE        DEFAULT NULL,
  `is_current` TINYINT     NOT NULL DEFAULT 0 COMMENT '是否当前学期',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_term_code` (`term_code`)
) ENGINE = InnoDB COMMENT = '学期表';

-- 11. 学生
CREATE TABLE IF NOT EXISTS `student` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `student_no`    VARCHAR(32) NOT NULL COMMENT '学号',
  `name`          VARCHAR(64) NOT NULL COMMENT '姓名',
  `gender`        VARCHAR(8)  DEFAULT NULL COMMENT '性别',
  `birthday`      DATE        DEFAULT NULL,
  `id_card_no`    VARCHAR(64) DEFAULT NULL COMMENT '身份证号(脱敏存储)',
  `phone`         VARCHAR(32) DEFAULT NULL COMMENT '手机号(脱敏存储)',
  `email`         VARCHAR(128) DEFAULT NULL,
  `clazz_id`      BIGINT      DEFAULT NULL,
  `major_id`      BIGINT      DEFAULT NULL,
  `college_id`    BIGINT      DEFAULT NULL,
  `grade`         VARCHAR(16) DEFAULT NULL COMMENT '年级',
  `enroll_date`   DATE        DEFAULT NULL COMMENT '入学日期',
  `political_status` VARCHAR(32) DEFAULT NULL COMMENT '政治面貌',
  `dormitory`     VARCHAR(64) DEFAULT NULL COMMENT '宿舍',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'ENROLLED' COMMENT 'ENROLLED在读/SUSPEND休学/GRADUATED毕业/DROPPED退学',
  `user_id`       BIGINT      DEFAULT NULL COMMENT '关联登录账号',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_student_clazz` (`clazz_id`),
  KEY `idx_student_college` (`college_id`)
) ENGINE = InnoDB COMMENT = '学生表';

-- 12. 教师
CREATE TABLE IF NOT EXISTS `teacher` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `teacher_no` VARCHAR(32) NOT NULL COMMENT '工号',
  `name`       VARCHAR(64) NOT NULL,
  `gender`     VARCHAR(8)  DEFAULT NULL,
  `college_id` BIGINT      DEFAULT NULL,
  `title`      VARCHAR(32) DEFAULT NULL COMMENT '职称',
  `degree`     VARCHAR(32) DEFAULT NULL COMMENT '学位学历',
  `research_area` VARCHAR(255) DEFAULT NULL COMMENT '研究方向',
  `phone`      VARCHAR(32) DEFAULT NULL,
  `email`      VARCHAR(128) DEFAULT NULL,
  `user_id`    BIGINT      DEFAULT NULL,
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`)
) ENGINE = InnoDB COMMENT = '教师表';

-- 13. 课程
CREATE TABLE IF NOT EXISTS `course` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `course_code` VARCHAR(32)  NOT NULL COMMENT '课程代码',
  `course_name` VARCHAR(128) NOT NULL COMMENT '课程名称',
  `credit`      DECIMAL(4,2) NOT NULL DEFAULT 0 COMMENT '学分',
  `course_type` VARCHAR(20)  DEFAULT NULL COMMENT 'PUBLIC公共课/CORE核心课/MAJOR专业课/ELECTIVE选修',
  `college_id`  BIGINT       DEFAULT NULL COMMENT '开课学院',
  `total_hours` INT          DEFAULT NULL COMMENT '总学时',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_code` (`course_code`)
) ENGINE = InnoDB COMMENT = '课程表';

-- 14. 开课/教学班
CREATE TABLE IF NOT EXISTS `course_offering` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `term_id`     BIGINT      NOT NULL COMMENT '学期',
  `course_id`   BIGINT      NOT NULL COMMENT '课程',
  `teacher_id`  BIGINT      DEFAULT NULL COMMENT '任课教师',
  `clazz_id`    BIGINT      DEFAULT NULL COMMENT '行政班(跨班选修为空)',
  `capacity`    INT         DEFAULT NULL,
  `classroom`   VARCHAR(64) DEFAULT NULL,
  `schedule`    VARCHAR(128) DEFAULT NULL COMMENT '上课时间',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_offering_term` (`term_id`),
  KEY `idx_offering_course` (`course_id`),
  KEY `idx_offering_teacher` (`teacher_id`)
) ENGINE = InnoDB COMMENT = '开课/教学班表';

-- =============================================================
-- 三、学情数据（4 张 多源采集）
-- =============================================================

-- 15. 成绩
CREATE TABLE IF NOT EXISTS `score` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `student_id`        BIGINT       NOT NULL,
  `course_offering_id` BIGINT      NOT NULL,
  `course_id`         BIGINT       NOT NULL,
  `term_id`           BIGINT       NOT NULL,
  `usual_score`       DECIMAL(5,2) DEFAULT NULL COMMENT '平时成绩',
  `mid_score`         DECIMAL(5,2) DEFAULT NULL COMMENT '期中成绩',
  `final_score`       DECIMAL(5,2) DEFAULT NULL COMMENT '期末成绩',
  `total_score`       DECIMAL(5,2) DEFAULT NULL COMMENT '总评成绩',
  `grade_point`       DECIMAL(4,2) DEFAULT NULL COMMENT '绩点',
  `exam_status`       VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL正常/DEFER缓考/ABSENT缺考/CHEAT作弊',
  `is_pass`           TINYINT      DEFAULT NULL COMMENT '是否及格',
  `is_retake`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否重修',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_stu_offering` (`student_id`, `course_offering_id`, `is_retake`),
  KEY `idx_score_student_term` (`student_id`, `term_id`),
  KEY `idx_score_course` (`course_id`)
) ENGINE = InnoDB COMMENT = '成绩表';

-- 16. 考勤
CREATE TABLE IF NOT EXISTS `attendance` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `student_id`        BIGINT      NOT NULL,
  `course_offering_id` BIGINT     NOT NULL,
  `term_id`           BIGINT      NOT NULL,
  `check_date`        DATE        NOT NULL COMMENT '考勤日期',
  `period`            VARCHAR(32) DEFAULT NULL COMMENT '节次',
  `status`            VARCHAR(20) NOT NULL COMMENT 'PRESENT出勤/LATE迟到/EARLY早退/LEAVE请假/ABSENT缺勤',
  `remark`            VARCHAR(255) DEFAULT NULL,
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`        TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_att_stu_date` (`student_id`, `check_date`),
  KEY `idx_att_offering` (`course_offering_id`)
) ENGINE = InnoDB COMMENT = '考勤记录表';

-- 17. 课堂表现
CREATE TABLE IF NOT EXISTS `class_performance` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `student_id`         BIGINT       NOT NULL,
  `course_offering_id` BIGINT       NOT NULL,
  `term_id`            BIGINT       NOT NULL,
  `record_date`        DATE         NOT NULL,
  `participation_score` DECIMAL(4,2) DEFAULT NULL COMMENT '课堂参与度(0-10)',
  `homework_score`     DECIMAL(5,2) DEFAULT NULL COMMENT '作业得分',
  `question_count`     INT          DEFAULT 0 COMMENT '提问/互动次数',
  `remark`             VARCHAR(255) DEFAULT NULL,
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_perf_stu` (`student_id`, `term_id`)
) ENGINE = InnoDB COMMENT = '课堂表现表';

-- 18. 在线学习行为
CREATE TABLE IF NOT EXISTS `online_behavior` (
  `id`                     BIGINT  NOT NULL AUTO_INCREMENT,
  `student_id`             BIGINT  NOT NULL,
  `course_id`              BIGINT  DEFAULT NULL,
  `term_id`                BIGINT  NOT NULL,
  `stat_date`              DATE    NOT NULL COMMENT '统计日期',
  `login_count`            INT     NOT NULL DEFAULT 0 COMMENT '登录次数',
  `study_minutes`          INT     NOT NULL DEFAULT 0 COMMENT '在线学习时长(分钟)',
  `video_progress`         DECIMAL(5,2) DEFAULT 0 COMMENT '视频完成度(%)',
  `resource_view_count`    INT     NOT NULL DEFAULT 0 COMMENT '资源查看次数',
  `forum_post_count`       INT     NOT NULL DEFAULT 0 COMMENT '论坛发帖数',
  `assignment_submit_count` INT    NOT NULL DEFAULT 0 COMMENT '作业提交次数',
  `create_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`             TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_behavior_stu_date` (`student_id`, `course_id`, `stat_date`),
  KEY `idx_behavior_term` (`term_id`)
) ENGINE = InnoDB COMMENT = '在线学习行为表';

-- =============================================================
-- 四、考试与命题质量分析（3 张，竞品新增 P2）
-- =============================================================

-- 19. 试卷
CREATE TABLE IF NOT EXISTS `exam_paper` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `course_offering_id` BIGINT     NOT NULL,
  `term_id`          BIGINT       NOT NULL,
  `paper_name`       VARCHAR(128) NOT NULL COMMENT '试卷名称',
  `total_score`      DECIMAL(5,2) DEFAULT NULL COMMENT '满分',
  `avg_score`        DECIMAL(5,2) DEFAULT NULL COMMENT '平均分',
  `max_score`        DECIMAL(5,2) DEFAULT NULL,
  `min_score`        DECIMAL(5,2) DEFAULT NULL,
  `std_dev`          DECIMAL(6,3) DEFAULT NULL COMMENT '标准差',
  `pass_rate`        DECIMAL(5,2) DEFAULT NULL COMMENT '及格率(%)',
  `excellent_rate`   DECIMAL(5,2) DEFAULT NULL COMMENT '优秀率(%)',
  `difficulty`       DECIMAL(4,3) DEFAULT NULL COMMENT '难度系数(0-1)',
  `discrimination`   DECIMAL(4,3) DEFAULT NULL COMMENT '区分度',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_paper_offering` (`course_offering_id`)
) ENGINE = InnoDB COMMENT = '试卷表(命题质量分析)';

-- 20. 试题
CREATE TABLE IF NOT EXISTS `exam_question` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `exam_paper_id`  BIGINT       NOT NULL,
  `question_no`    VARCHAR(16)  NOT NULL COMMENT '题号',
  `question_type`  VARCHAR(32)  DEFAULT NULL COMMENT '题型',
  `knowledge_point` VARCHAR(128) DEFAULT NULL COMMENT '知识点',
  `full_score`     DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '满分',
  `avg_score`      DECIMAL(5,2) DEFAULT NULL COMMENT '平均得分',
  `score_rate`     DECIMAL(5,2) DEFAULT NULL COMMENT '得分率(%)',
  `difficulty`     DECIMAL(4,3) DEFAULT NULL COMMENT '难度',
  `discrimination` DECIMAL(4,3) DEFAULT NULL COMMENT '区分度',
  `std_dev`        DECIMAL(6,3) DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_question_paper` (`exam_paper_id`),
  KEY `idx_question_kp` (`knowledge_point`)
) ENGINE = InnoDB COMMENT = '试题表(知识点归因)';

-- 21. 学生小题得分
CREATE TABLE IF NOT EXISTS `exam_question_score` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `exam_paper_id`    BIGINT       NOT NULL,
  `exam_question_id` BIGINT       NOT NULL,
  `student_id`       BIGINT       NOT NULL,
  `score`            DECIMAL(5,2) NOT NULL DEFAULT 0,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_qscore_stu` (`student_id`),
  KEY `idx_qscore_question` (`exam_question_id`)
) ENGINE = InnoDB COMMENT = '学生小题得分表(知识点掌握度)';

-- =============================================================
-- 五、学生画像（3 张，竞品新增 P1）
-- =============================================================

-- 22. 标签定义
CREATE TABLE IF NOT EXISTS `tag_definition` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `tag_code`    VARCHAR(64) NOT NULL COMMENT '标签编码',
  `tag_name`    VARCHAR(64) NOT NULL COMMENT '标签名称',
  `tag_category` VARCHAR(20) DEFAULT NULL COMMENT 'ACADEMIC学业/BEHAVIOR行为/DEVELOPMENT发展/RISK风险',
  `rule_json`   TEXT                 COMMENT '自动打标规则(JSON)',
  `color`       VARCHAR(16) DEFAULT NULL,
  `is_auto`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否自动打标',
  `description` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`)
) ENGINE = InnoDB COMMENT = '标签定义表';

-- 23. 学生标签
CREATE TABLE IF NOT EXISTS `student_tag` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `student_id`     BIGINT      NOT NULL,
  `tag_id`         BIGINT      NOT NULL,
  `term_id`        BIGINT      DEFAULT NULL,
  `tag_value`      VARCHAR(64) DEFAULT NULL COMMENT '标签值',
  `source`         VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO自动/MANUAL手动',
  `effective_date` DATE        DEFAULT NULL,
  `expire_date`    DATE        DEFAULT NULL,
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_stag_student` (`student_id`),
  KEY `idx_stag_tag` (`tag_id`)
) ENGINE = InnoDB COMMENT = '学生标签表';

-- 24. 画像快照
CREATE TABLE IF NOT EXISTS `profile_snapshot` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `student_id`      BIGINT       NOT NULL,
  `term_id`         BIGINT       DEFAULT NULL,
  `academic_score`  DECIMAL(5,2) DEFAULT NULL COMMENT '学业力(0-100)',
  `engagement_score` DECIMAL(5,2) DEFAULT NULL COMMENT '学习投入(0-100)',
  `behavior_score`  DECIMAL(5,2) DEFAULT NULL COMMENT '行为规律(0-100)',
  `potential_score` DECIMAL(5,2) DEFAULT NULL COMMENT '发展潜力(0-100)',
  `risk_score`      DECIMAL(5,2) DEFAULT NULL COMMENT '风险分(0-100)',
  `dimension_json`  TEXT                  COMMENT '五维明细(JSON)',
  `snapshot_date`   DATE         NOT NULL COMMENT '快照日期',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_stu` (`student_id`, `snapshot_date`)
) ENGINE = InnoDB COMMENT = '学生画像快照表';

-- =============================================================
-- 六、预测模型与结果（3 张）
-- =============================================================

-- 25. 机器学习模型
CREATE TABLE IF NOT EXISTS `ml_model` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `model_code`        VARCHAR(64) NOT NULL COMMENT '模型编码',
  `model_name`        VARCHAR(64) NOT NULL COMMENT '模型名称',
  `algorithm`         VARCHAR(32) NOT NULL COMMENT 'LR/RF/XGBOOST',
  `version`           VARCHAR(32) NOT NULL DEFAULT 'v1.0',
  `status`            VARCHAR(20) NOT NULL DEFAULT 'TRAINING' COMMENT 'TRAINING训练中/ONLINE已上线/OFFLINE已下线',
  `metrics_json`      TEXT                 COMMENT '评估指标(JSON): accuracy/precision/recall/f1/auc/rmse',
  `feature_list_json` TEXT                 COMMENT '特征列表(JSON)',
  `model_path`        VARCHAR(255) DEFAULT NULL COMMENT '模型文件路径',
  `train_sample_count` INT        DEFAULT NULL,
  `train_time`        DATETIME    DEFAULT NULL,
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`        TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code_version` (`model_code`, `version`)
) ENGINE = InnoDB COMMENT = '机器学习模型表';

-- 26. 预测结果
CREATE TABLE IF NOT EXISTS `prediction_result` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `student_id`       BIGINT       NOT NULL,
  `course_id`        BIGINT       DEFAULT NULL COMMENT '课程ID(为空表示综合预测)',
  `term_id`          BIGINT       NOT NULL,
  `model_id`         BIGINT       DEFAULT NULL,
  `predicted_score`  DECIMAL(5,2) DEFAULT NULL COMMENT '预测成绩',
  `predicted_gpa`    DECIMAL(4,2) DEFAULT NULL COMMENT '预测绩点',
  `risk_level`       TINYINT      DEFAULT NULL COMMENT '风险等级 1蓝2黄3橙4红',
  `risk_probability` DECIMAL(5,4) DEFAULT NULL COMMENT '风险概率(0-1)',
  `explain_json`     TEXT                  COMMENT '可解释结果(SHAP特征贡献 JSON)',
  `actual_score`     DECIMAL(5,2) DEFAULT NULL COMMENT '实际成绩(事后回填,用于评估)',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_pred_stu_term` (`student_id`, `term_id`),
  KEY `idx_pred_risk` (`risk_level`)
) ENGINE = InnoDB COMMENT = '学业预测结果表';

-- 27. 特征记录（供模型训练与回溯）
CREATE TABLE IF NOT EXISTS `feature_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `student_id`  BIGINT      NOT NULL,
  `term_id`     BIGINT      NOT NULL,
  `course_id`   BIGINT      DEFAULT NULL,
  `feature_json` TEXT       NOT NULL COMMENT '特征向量(JSON)',
  `label_value` DECIMAL(5,2) DEFAULT NULL COMMENT '标签值(真实成绩/是否挂科)',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_feature_stu_term` (`student_id`, `term_id`)
) ENGINE = InnoDB COMMENT = '特征记录表';

-- =============================================================
-- 七、学业预警与干预闭环（4 张，核心差异化）
-- =============================================================

-- 28. 预警规则
CREATE TABLE IF NOT EXISTS `warning_rule` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `rule_code`       VARCHAR(64) NOT NULL COMMENT '规则编码',
  `rule_name`       VARCHAR(64) NOT NULL COMMENT '规则名称',
  `warning_type`    VARCHAR(32) NOT NULL COMMENT 'ACADEMIC学业/ATTENDANCE出勤/HOMEWORK作业/FAIL挂科/PROGRESS进度',
  `level`           TINYINT     NOT NULL DEFAULT 2 COMMENT '预警等级 1蓝2黄3橙4红',
  `condition_json`  TEXT        NOT NULL COMMENT '触发条件(JSON),支持阈值与评分卡',
  `weight`          DECIMAL(4,2) DEFAULT 1.00 COMMENT '评分卡权重',
  `score_threshold` DECIMAL(5,2) DEFAULT NULL COMMENT '加权总分阈值',
  `enabled`         TINYINT     NOT NULL DEFAULT 1,
  `description`     VARCHAR(255) DEFAULT NULL,
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`)
) ENGINE = InnoDB COMMENT = '学业预警规则表';

-- 29. 预警记录
CREATE TABLE IF NOT EXISTS `warning_record` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `student_id`    BIGINT      NOT NULL,
  `term_id`       BIGINT      NOT NULL,
  `rule_id`       BIGINT      DEFAULT NULL,
  `prediction_id` BIGINT      DEFAULT NULL COMMENT '关联预测结果',
  `warning_type`  VARCHAR(32) NOT NULL,
  `level`         TINYINT     NOT NULL COMMENT '1蓝2黄3橙4红',
  `title`         VARCHAR(128) NOT NULL COMMENT '预警标题',
  `content`       TEXT                 COMMENT '预警内容',
  `trigger_value` VARCHAR(128) DEFAULT NULL COMMENT '触发值',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理/PROCESSING处理中/RESOLVED已解除/CLOSED已关闭',
  `assigned_to`   BIGINT      DEFAULT NULL COMMENT '责任人(用户ID)',
  `assigned_time` DATETIME    DEFAULT NULL,
  `resolved_time` DATETIME    DEFAULT NULL,
  `resolve_remark` VARCHAR(255) DEFAULT NULL,
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_warn_stu` (`student_id`),
  KEY `idx_warn_status` (`status`),
  KEY `idx_warn_level` (`level`),
  KEY `idx_warn_term` (`term_id`)
) ENGINE = InnoDB COMMENT = '学业预警记录表';

-- 30. 干预工单（闭环核心）
CREATE TABLE IF NOT EXISTS `intervention_ticket` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `ticket_no`     VARCHAR(64) NOT NULL COMMENT '工单编号',
  `warning_id`    BIGINT      NOT NULL COMMENT '关联预警',
  `student_id`    BIGINT      NOT NULL,
  `owner_user_id` BIGINT      DEFAULT NULL COMMENT '负责人(辅导员/导师)',
  `intervene_type` VARCHAR(20) DEFAULT NULL COMMENT 'GUIDE导学/HELP助学/SUPERVISE督学',
  `plan`          TEXT                 COMMENT '帮扶计划',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'WAIT_ACCEPT' COMMENT 'WAIT_ACCEPT待接单/IN_PROGRESS进行中/WAIT_EVAL待评估/CLOSED已闭环/CANCELLED已取消',
  `start_date`    DATE        DEFAULT NULL,
  `due_date`      DATE        DEFAULT NULL,
  `close_date`    DATE        DEFAULT NULL,
  `conclusion`    TEXT                 COMMENT '结案结论',
  `effect_eval`   VARCHAR(20) DEFAULT NULL COMMENT 'IMPROVED改善/STABLE持平/INVALID无效',
  `create_by`     BIGINT      DEFAULT NULL,
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_ticket_warning` (`warning_id`),
  KEY `idx_ticket_owner` (`owner_user_id`),
  KEY `idx_ticket_status` (`status`)
) ENGINE = InnoDB COMMENT = '预警干预工单表(闭环)';

-- 31. 干预过程记录（台账）
CREATE TABLE IF NOT EXISTS `intervention_log` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `ticket_id`      BIGINT      NOT NULL,
  `action_type`    VARCHAR(32) DEFAULT NULL COMMENT '动作类型: 谈心/补课/家长沟通/学习督导等',
  `action_content` TEXT        NOT NULL COMMENT '帮扶内容',
  `action_date`    DATE        NOT NULL,
  `operator_id`    BIGINT      DEFAULT NULL COMMENT '操作人',
  `effect_eval`    VARCHAR(255) DEFAULT NULL COMMENT '本次效果评估',
  `attachment_url` VARCHAR(255) DEFAULT NULL,
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ilog_ticket` (`ticket_id`)
) ENGINE = InnoDB COMMENT = '干预过程台账表';

-- =============================================================
-- 八、消息、AI 与系统治理（7 张）
-- =============================================================

-- 32. 通知消息与待办
CREATE TABLE IF NOT EXISTS `notify_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '接收人',
  `message_type` VARCHAR(20) NOT NULL COMMENT 'WARNING预警/TODO待办/SYSTEM系统',
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT,
  `biz_type`    VARCHAR(32)  DEFAULT NULL COMMENT '业务类型',
  `biz_id`      BIGINT       DEFAULT NULL COMMENT '业务ID',
  `channel`     VARCHAR(20)  NOT NULL DEFAULT 'INBOX' COMMENT 'INBOX站内/EMAIL邮件/WECHAT微信',
  `is_read`     TINYINT      NOT NULL DEFAULT 0,
  `read_time`   DATETIME     DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_notify_user` (`user_id`, `is_read`)
) ENGINE = InnoDB COMMENT = '通知消息表';

-- 33. 审计日志
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       DEFAULT NULL,
  `username`   VARCHAR(64)  DEFAULT NULL,
  `module`     VARCHAR(64)  DEFAULT NULL COMMENT '模块',
  `operation`  VARCHAR(128) DEFAULT NULL COMMENT '操作描述',
  `method`     VARCHAR(255) DEFAULT NULL COMMENT 'Java方法',
  `url`        VARCHAR(255) DEFAULT NULL,
  `params`     TEXT                 COMMENT '请求参数(脱敏后)',
  `ip`         VARCHAR(64)  DEFAULT NULL,
  `user_agent` VARCHAR(255) DEFAULT NULL,
  `cost_ms`    INT          DEFAULT NULL COMMENT '耗时(ms)',
  `success`    TINYINT      NOT NULL DEFAULT 1,
  `error_msg`  VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_time` (`create_time`)
) ENGINE = InnoDB COMMENT = '操作审计日志表';

-- 34. 数据导入任务
CREATE TABLE IF NOT EXISTS `import_task` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `task_no`          VARCHAR(64) NOT NULL,
  `import_type`      VARCHAR(32) NOT NULL COMMENT 'STUDENT/SCORE/ATTENDANCE/PERFORMANCE/BEHAVIOR',
  `file_name`        VARCHAR(255) DEFAULT NULL,
  `file_url`         VARCHAR(255) DEFAULT NULL,
  `total_count`      INT         NOT NULL DEFAULT 0,
  `success_count`    INT         NOT NULL DEFAULT 0,
  `fail_count`       INT         NOT NULL DEFAULT 0,
  `status`           VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/PARTIAL',
  `error_report_url` VARCHAR(255) DEFAULT NULL COMMENT '错误报告文件',
  `operator_id`      BIGINT      DEFAULT NULL,
  `start_time`       DATETIME    DEFAULT NULL,
  `end_time`         DATETIME    DEFAULT NULL,
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_task_no` (`task_no`)
) ENGINE = InnoDB COMMENT = '数据导入任务表';

-- 35. 数据质量报告
CREATE TABLE IF NOT EXISTS `data_quality_report` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `task_id`      BIGINT      DEFAULT NULL,
  `dataset`      VARCHAR(64) DEFAULT NULL COMMENT '数据集',
  `check_rule`   VARCHAR(128) DEFAULT NULL COMMENT '校验规则',
  `problem_type` VARCHAR(32) DEFAULT NULL COMMENT 'MISSING缺失/OUT_OF_RANGE越界/DUPLICATE重复/INCONSISTENT不一致',
  `problem_count` INT        NOT NULL DEFAULT 0,
  `sample_json`  TEXT                 COMMENT '问题样本(JSON)',
  `check_time`   DATETIME    DEFAULT NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_dqr_task` (`task_id`)
) ENGINE = InnoDB COMMENT = '数据质量报告表';

-- 36. 提示词模板
CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `template_code`  VARCHAR(64) NOT NULL,
  `template_name`  VARCHAR(128) NOT NULL,
  `scene`          VARCHAR(32) DEFAULT NULL COMMENT 'REPORT报告/ADVICE建议/EXPLAIN解释/QA问答',
  `content`        TEXT        NOT NULL COMMENT '模板内容(含变量占位符)',
  `variables_json` TEXT                 COMMENT '变量定义(JSON)',
  `version`        INT         NOT NULL DEFAULT 1,
  `enabled`        TINYINT     NOT NULL DEFAULT 1,
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tpl_scene` (`scene`)
) ENGINE = InnoDB COMMENT = 'AI提示词模板表';

-- 37. AI 调用日志
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_type`          VARCHAR(32) DEFAULT NULL COMMENT '业务类型',
  `biz_id`            BIGINT      DEFAULT NULL,
  `model_name`        VARCHAR(64) DEFAULT NULL COMMENT '模型名称',
  `template_id`       BIGINT      DEFAULT NULL,
  `prompt_tokens`     INT         DEFAULT 0,
  `completion_tokens` INT         DEFAULT 0,
  `total_tokens`      INT         DEFAULT 0,
  `cost_ms`           INT         DEFAULT NULL,
  `status`            VARCHAR(20) DEFAULT NULL COMMENT 'SUCCESS/FAILED/FALLBACK',
  `error_msg`         VARCHAR(500) DEFAULT NULL,
  `user_id`           BIGINT      DEFAULT NULL,
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ailog_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB COMMENT = 'AI调用日志表';

-- 38. 学情报告记录
CREATE TABLE IF NOT EXISTS `report_record` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `report_type`  VARCHAR(20) NOT NULL COMMENT 'PERSONAL个人/CLASS班级/COURSE课程',
  `biz_id`       BIGINT      NOT NULL COMMENT '学生/班级/课程ID',
  `term_id`      BIGINT      DEFAULT NULL,
  `model_name`   VARCHAR(64) DEFAULT NULL,
  `file_url`     VARCHAR(255) DEFAULT NULL,
  `file_format`  VARCHAR(10) DEFAULT 'WORD' COMMENT 'WORD/PDF',
  `status`       VARCHAR(20) NOT NULL DEFAULT 'GENERATING' COMMENT 'GENERATING/SUCCESS/FAILED',
  `generator_id` BIGINT      DEFAULT NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_report_biz` (`report_type`, `biz_id`)
) ENGINE = InnoDB COMMENT = '学情报告生成记录表';

-- 39. RAG 知识文档
CREATE TABLE IF NOT EXISTS `knowledge_doc` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_name`    VARCHAR(255) NOT NULL COMMENT '文档名称',
  `doc_type`    VARCHAR(32)  DEFAULT NULL COMMENT '培养方案/课程大纲/校规/其他',
  `file_url`    VARCHAR(255) DEFAULT NULL,
  `content_hash` VARCHAR(64) DEFAULT NULL,
  `chunk_count` INT          NOT NULL DEFAULT 0,
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED',
  `uploader_id` BIGINT       DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = 'RAG知识文档表';

-- 40. RAG 文档切片
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
  `id`          BIGINT  NOT NULL AUTO_INCREMENT,
  `doc_id`      BIGINT  NOT NULL,
  `chunk_index` INT     NOT NULL,
  `content`     TEXT    NOT NULL COMMENT '切片内容',
  `token_count` INT     DEFAULT NULL,
  `vector_id`   VARCHAR(64) DEFAULT NULL COMMENT '向量库ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_chunk_doc` (`doc_id`)
) ENGINE = InnoDB COMMENT = 'RAG文档切片表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 表清单
-- 一、系统与安全：sys_user / sys_role / sys_user_role / sys_menu / sys_role_menu / sys_config            (6)
-- 二、组织与基础：college / major / clazz / term / student / teacher / course / course_offering          (8)
-- 三、学情数据：  score / attendance / class_performance / online_behavior                               (4)
-- 四、考试命题：  exam_paper / exam_question / exam_question_score                                       (3)
-- 五、学生画像：  tag_definition / student_tag / profile_snapshot                                        (3)
-- 六、预测模型：  ml_model / prediction_result / feature_record                                          (3)
-- 七、预警干预：  warning_rule / warning_record / intervention_ticket / intervention_log                 (4)
-- 八、消息与治理：notify_message / audit_log / import_task / data_quality_report                         (4)
--                 ai_prompt_template / ai_call_log / report_record / knowledge_doc / knowledge_chunk     (5)
-- 合计 40 张表
-- =============================================================
