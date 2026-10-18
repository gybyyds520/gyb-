-- =========================================================
-- Phase 12 增量：学习目标（目标设定 + 资源推荐）
-- =========================================================
USE xqfx;

CREATE TABLE IF NOT EXISTS learning_goal (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    student_id   BIGINT       NOT NULL                COMMENT '学生ID',
    term_id      BIGINT       NULL                    COMMENT '学期ID，为空表示累计目标',
    goal_type    VARCHAR(20)  NOT NULL                COMMENT 'AVG_SCORE均分/GPA绩点/COURSE单科',
    target_value DECIMAL(6,2) NOT NULL                COMMENT '目标值',
    course_id    BIGINT       NULL                    COMMENT '单科目标时指定课程',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE进行中/ACHIEVED已达成/ABANDONED已放弃',
    deadline     DATE         NULL                    COMMENT '截止日期',
    note         VARCHAR(500) NULL                    COMMENT '目标说明',
    create_time  datetime     NULL,
    update_time  datetime     NULL,
    is_deleted   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_goal_student (student_id, status),
    KEY idx_goal_term (term_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习目标';

SELECT id, student_id, goal_type, target_value, status FROM learning_goal;
