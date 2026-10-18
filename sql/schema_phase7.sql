-- ============================================================
-- Phase 7：教师 / 学生 / 家长 三端角色隔离
--   1) 新增家长角色 PARENT
--   2) 新建家长-学生绑定关系表 student_parent
--   3) sys_user 增加「关联业务主体」字段，用于三端身份解析
-- 幂等：可重复执行
-- ============================================================

USE `xqfx`;

DROP PROCEDURE IF EXISTS xqfx_add_column;
DELIMITER $$
CREATE PROCEDURE xqfx_add_column(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'xqfx' AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1) 家长注册码：家长自助绑定学生时的校验依据（学生表）
CALL xqfx_add_column('student', 'parent_bind_code', 'varchar(32) NULL COMMENT ''家长绑定码''');

-- 2) 用户登录身份类别冗余，便于按角色检索（不影响原 user_type）
CALL xqfx_add_column('sys_user', 'student_id', 'bigint NULL COMMENT ''关联学生ID''');
CALL xqfx_add_column('sys_user', 'teacher_id', 'bigint NULL COMMENT ''关联教师ID''');

-- 3) 家长-学生绑定关系
CREATE TABLE IF NOT EXISTS `student_parent` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_id`     BIGINT       NOT NULL                COMMENT '学生ID',
    `parent_user_id` BIGINT       NOT NULL                COMMENT '家长登录账号ID',
    `relation`       VARCHAR(20)  DEFAULT 'GUARDIAN'      COMMENT '关系: FATHER父亲/MOTHER母亲/GUARDIAN监护人',
    `parent_name`    VARCHAR(50)  DEFAULT NULL            COMMENT '家长姓名',
    `parent_phone`   VARCHAR(32)  DEFAULT NULL            COMMENT '家长联系电话(脱敏)',
    `is_primary`     TINYINT      DEFAULT 1               COMMENT '是否主监护人: 1是 0否',
    `status`         TINYINT      DEFAULT 1               COMMENT '状态: 1启用 0停用',
    `create_time`    DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT NULL            COMMENT '更新时间',
    `is_deleted`     TINYINT      DEFAULT 0               COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_student` (`parent_user_id`, `student_id`),
    KEY `idx_student` (`student_id`),
    KEY `idx_parent_user` (`parent_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '家长与学生绑定关系';

-- 4) 家长端回执：家长对学校预警/帮扶措施的确认
CREATE TABLE IF NOT EXISTS `parent_feedback` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_id`   BIGINT      NOT NULL                COMMENT '学生ID',
    `parent_id`    BIGINT      NOT NULL                COMMENT '家长账号ID',
    `biz_type`     VARCHAR(32) DEFAULT 'WARNING'       COMMENT '业务类型: WARNING预警/INTERVENTION帮扶',
    `biz_id`       BIGINT      DEFAULT NULL            COMMENT '业务ID',
    `ack_status`   VARCHAR(20) DEFAULT 'ACKNOWLEDGED'  COMMENT '回执: ACKNOWLEDGED已阅/SUPPORTED配合/OBJECTED有异议',
    `content`      VARCHAR(500) DEFAULT NULL           COMMENT '家长留言',
    `create_time`  DATETIME    DEFAULT NULL            COMMENT '创建时间',
    `update_time`  DATETIME    DEFAULT NULL            COMMENT '更新时间',
    `is_deleted`   TINYINT     DEFAULT 0               COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_student` (`student_id`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '家长回执';

-- 5) 新增家长角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort`, `status`)
SELECT 7, 'PARENT', '家长', '查看子女学业概况与预警提示并回执', 7, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'PARENT');

DROP PROCEDURE IF EXISTS xqfx_add_column;

SELECT role_code, role_name FROM `sys_role` ORDER BY `sort`;
SHOW TABLES LIKE 'student_parent';
SHOW TABLES LIKE 'parent_feedback';
