-- =========================================================
-- Phase 3 增量迁移：知识库向量列（幂等，可重复执行）
-- =========================================================
USE xqfx;

DROP PROCEDURE IF EXISTS xqfx_add_column;
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
DELIMITER ;

-- 存储分片向量（JSON 数组），便于在 Java 侧做余弦相似度检索
CALL xqfx_add_column('knowledge_chunk', 'embedding_json', "TEXT NULL COMMENT '分片向量（JSON数组）'");

DROP PROCEDURE IF EXISTS xqfx_add_column;
