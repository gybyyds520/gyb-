-- =========================================================
-- Phase 2 增量迁移（幂等，可重复执行）
-- 说明：warning_record 表在 Phase 0 已建立，此处补齐预警闭环所需字段。
--       MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故用存储过程实现幂等。
-- =========================================================
USE xqfx;

DROP PROCEDURE IF EXISTS xqfx_add_column;
DROP PROCEDURE IF EXISTS xqfx_add_index;

DELIMITER $$
CREATE PROCEDURE xqfx_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE xqfx_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1. 预警记录表补齐字段（复用既有 assigned_to / assigned_time / resolve_remark）
CALL xqfx_add_column('warning_record', 'warning_no',       "VARCHAR(64) NULL COMMENT '预警编号'");
CALL xqfx_add_column('warning_record', 'warning_type',     "VARCHAR(32) NULL COMMENT '预警类型：ACADEMIC/ATTENDANCE/BEHAVIOR/COMPREHENSIVE'");
CALL xqfx_add_column('warning_record', 'trigger_detail',   "TEXT NULL COMMENT '触发详情（JSON）'");
CALL xqfx_add_column('warning_record', 'risk_probability', "DECIMAL(5,4) NULL COMMENT '风险概率'");

-- 2. 干预工单补充来源预警
CALL xqfx_add_column('intervention_ticket', 'warning_id', "BIGINT NULL COMMENT '来源预警ID'");

-- 3. 索引
CALL xqfx_add_index('warning_record', 'idx_wr_student', 'student_id');
CALL xqfx_add_index('warning_record', 'idx_wr_level_status', 'level, status');
CALL xqfx_add_index('warning_record', 'idx_wr_term', 'term_id');

DROP PROCEDURE IF EXISTS xqfx_add_column;
DROP PROCEDURE IF EXISTS xqfx_add_index;

-- 4. 说明：四级预警规则已在 seed_base.sql 中初始化
--    （RULE_FAIL_RISK_RED/ORANGE、RULE_ATTENDANCE、RULE_HOMEWORK、RULE_SCORE_DROP、RULE_CREDIT_PROGRESS）
--    阈值采用 0-100 风险分制，模型输出的 0-1 概率由 WarningService 换算后比对。
