-- =========================================================
-- Phase 7 增量：试卷分析与课堂表现所需字段
-- exam_question_score 原表缺少 update_time，实体继承 BaseEntity 后插入会报错
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

CALL xqfx_add_column('exam_question_score', 'update_time', 'datetime NULL COMMENT ''更新时间''');
CALL xqfx_add_column('exam_paper', 'update_time', 'datetime NULL COMMENT ''更新时间''');
CALL xqfx_add_column('exam_question', 'update_time', 'datetime NULL COMMENT ''更新时间''');
CALL xqfx_add_column('data_quality_report', 'update_time', 'datetime NULL COMMENT ''更新时间''');

DROP PROCEDURE IF EXISTS xqfx_add_column;

SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'xqfx'
  AND TABLE_NAME IN ('exam_paper', 'exam_question', 'exam_question_score', 'data_quality_report')
  AND COLUMN_NAME IN ('create_time', 'update_time', 'is_deleted')
ORDER BY TABLE_NAME, COLUMN_NAME;
