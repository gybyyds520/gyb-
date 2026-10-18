-- =========================================================
-- Phase 9 增量：站内通讯（教师 ↔ 学生 ↔ 家长）
-- =========================================================
USE xqfx;

CREATE TABLE IF NOT EXISTS im_message (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    sender_id    BIGINT   NOT NULL                COMMENT '发送人（sys_user.id）',
    receiver_id  BIGINT   NOT NULL                COMMENT '接收人（sys_user.id）',
    content      TEXT     NOT NULL                COMMENT '消息内容',
    is_read      tinyint  NOT NULL DEFAULT 0      COMMENT '是否已读 0未读 1已读',
    read_time    datetime NULL                    COMMENT '阅读时间',
    create_time  datetime NULL                    COMMENT '创建时间',
    update_time  datetime NULL                    COMMENT '更新时间',
    is_deleted   tinyint  NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_im_receiver (receiver_id, is_read),
    KEY idx_im_pair (sender_id, receiver_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通讯消息';

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'xqfx' AND TABLE_NAME = 'im_message'
ORDER BY ORDINAL_POSITION;
