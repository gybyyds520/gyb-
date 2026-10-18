-- =========================================================
-- Phase 8 增量：工单时效（SLA）与干预反馈
-- 对标行业实践：预警工单需设定承诺处理时限、超时自动升级、
-- 并支持辅导员对预警有效性打标，用于规则调优
-- =========================================================
USE xqfx;

DROP PROCEDURE IF EXISTS xqfx_add_column;
DELIMITER $$
CREATE PROCEDURE xqfx_add_column(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 承诺处理时限（小时），按预警等级自动设定
CALL xqfx_add_column('intervention_ticket', 'sla_hours', 'int NULL COMMENT ''承诺处理时限（小时）''');
-- 超时时长（小时），未超时为 0
CALL xqfx_add_column('intervention_ticket', 'overdue_hours', 'int NULL DEFAULT 0 COMMENT ''超时时长（小时）''');
-- 升级层级：0 未升级 / 1 升至院系管理员 / 2 升至校级学工负责人
CALL xqfx_add_column('intervention_ticket', 'escalate_level', 'int NULL DEFAULT 0 COMMENT ''升级层级''');
-- 辅导员反馈：EFFECTIVE有效 / FALSE_POSITIVE误报 / INCOMPLETE信息不全 / ADJUST建议调整
CALL xqfx_add_column('intervention_ticket', 'feedback', 'varchar(32) NULL COMMENT ''预警有效性反馈''');
CALL xqfx_add_column('intervention_ticket', 'feedback_note', 'varchar(500) NULL COMMENT ''反馈说明''');
CALL xqfx_add_column('intervention_ticket', 'last_remind_time', 'datetime NULL COMMENT ''最近催办时间''');
CALL xqfx_add_column('intervention_ticket', 'update_time', 'datetime NULL COMMENT ''更新时间''');

DROP PROCEDURE IF EXISTS xqfx_add_column;

-- 补齐历史工单的 SLA 时限：按关联预警等级设定，缺省按黄色 72 小时
UPDATE intervention_ticket t
LEFT JOIN warning_record w ON w.id = t.warning_id
SET t.sla_hours = CASE w.level
    WHEN 1 THEN 168
    WHEN 2 THEN 72
    WHEN 3 THEN 24
    WHEN 4 THEN 8
    ELSE 72
END
WHERE t.sla_hours IS NULL;

SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'xqfx'
  AND TABLE_NAME = 'intervention_ticket'
  AND COLUMN_NAME IN ('sla_hours', 'overdue_hours', 'escalate_level', 'feedback', 'feedback_note', 'last_remind_time')
ORDER BY COLUMN_NAME;
