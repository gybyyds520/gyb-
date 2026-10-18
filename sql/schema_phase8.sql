-- =========================================================
-- Phase 8 增量：错题「举一反三」巩固练习
-- 1) exam_question 补充题干 / 选项 / 答案 / 解析，供本地大模型生成同类题
-- 2) 新建 exam_practice 保存生成的巩固练习，学生端可回看
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

-- 题目内容：本地大模型生成同类题需要原题作为依据
CALL xqfx_add_column('exam_question', 'stem', 'TEXT NULL COMMENT ''题干''');
CALL xqfx_add_column('exam_question', 'options_json', 'TEXT NULL COMMENT ''选项JSON数组''');
CALL xqfx_add_column('exam_question', 'answer', 'VARCHAR(255) NULL COMMENT ''参考答案''');
CALL xqfx_add_column('exam_question', 'analysis', 'TEXT NULL COMMENT ''试题解析''');

DROP PROCEDURE IF EXISTS xqfx_add_column;

-- 举一反三生成的巩固练习
CREATE TABLE IF NOT EXISTS exam_practice (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    exam_paper_id       BIGINT       NULL     COMMENT '所属试卷',
    exam_question_id    BIGINT       NOT NULL COMMENT '来源错题（题目ID）',
    student_id          BIGINT       NULL     COMMENT '目标学生，为空表示面向全班',
    course_name         VARCHAR(128) NULL     COMMENT '课程名称',
    knowledge_point     VARCHAR(128) NULL     COMMENT '考查知识点',
    question_type       VARCHAR(32)  NULL     COMMENT '题型',
    source_type         VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT 'AI本地模型 / RULE规则降级',
    model_name          VARCHAR(64)  NULL     COMMENT '实际使用的本地模型',
    question_count      INT          NOT NULL DEFAULT 0 COMMENT '生成题目数',
    content_json        TEXT         NULL     COMMENT '练习题JSON数组',
    prompt_tokens       INT          NOT NULL DEFAULT 0,
    completion_tokens   INT          NOT NULL DEFAULT 0,
    cost_ms             INT          NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED / FALLBACK',
    error_msg           VARCHAR(512) NULL,
    user_id             BIGINT       NULL     COMMENT '生成人',
    create_time         datetime     NULL     COMMENT '创建时间',
    update_time         datetime     NULL     COMMENT '更新时间',
    is_deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_practice_question (exam_question_id),
    KEY idx_practice_student (student_id),
    KEY idx_practice_paper (exam_paper_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '错题举一反三巩固练习';

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'xqfx'
  AND ((TABLE_NAME = 'exam_question' AND COLUMN_NAME IN ('stem', 'options_json', 'answer', 'analysis'))
       OR TABLE_NAME = 'exam_practice')
ORDER BY TABLE_NAME, COLUMN_NAME;
