-- =========================================================
-- Phase 10 增量：学生 / 家长会员（VIP）等级与充值
-- 说明：仅学生与家长端参与会员体系，教师端不使用
-- =========================================================
USE xqfx;

-- 会员等级
CREATE TABLE IF NOT EXISTS vip_level (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    level_code   VARCHAR(20)  NOT NULL                COMMENT '等级编码 VIP1/VIP2/VIP3',
    name         VARCHAR(30)  NOT NULL                COMMENT '等级名称',
    level_order  INT          NOT NULL DEFAULT 1      COMMENT '等级次序，越大越高',
    price_month  DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '月费',
    price_year   DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '年费',
    benefits     TEXT         NULL                    COMMENT '权益说明（JSON 数组）',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
    create_time  datetime     NULL,
    update_time  datetime     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vip_level_code (level_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员等级';

-- 充值订单
CREATE TABLE IF NOT EXISTS vip_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32)  NOT NULL                COMMENT '订单号',
    user_id      BIGINT       NOT NULL                COMMENT '下单用户',
    level_code   VARCHAR(20)  NOT NULL                COMMENT '购买等级',
    period       VARCHAR(10)  NOT NULL                COMMENT 'MONTH 月 / YEAR 年',
    amount       DECIMAL(10,2) NOT NULL               COMMENT '应付金额',
    pay_status   VARCHAR(16)  NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID待支付/PAID已支付/CLOSED已关闭',
    pay_method   VARCHAR(16)  NULL                    COMMENT 'MOCK模拟/WECHAT/ALIPAY',
    pay_time     datetime     NULL,
    create_time  datetime     NULL,
    update_time  datetime     NULL,
    is_deleted   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vip_order_no (order_no),
    KEY idx_vip_order_user (user_id, pay_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员充值订单';

-- 用户会员（一人一条，记录当前等级与到期时间）
CREATE TABLE IF NOT EXISTS user_vip (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL                COMMENT '用户ID',
    level_code    VARCHAR(20) NOT NULL                COMMENT '当前等级',
    expire_time   datetime    NOT NULL                COMMENT '到期时间',
    total_amount  DECIMAL(10,2) NOT NULL DEFAULT 0    COMMENT '累计充值金额',
    status        TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0失效',
    create_time   datetime    NULL,
    update_time   datetime    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_vip_user (user_id),
    KEY idx_user_vip_expire (expire_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户会员';

-- 初始等级
INSERT INTO vip_level (level_code, name, level_order, price_month, price_year, benefits, status, create_time)
SELECT t.*, 1, NOW() FROM (
    SELECT 'VIP1' AS c1, '进阶会员' AS c2, 1 AS c3, 30.00 AS c4, 298.00 AS c5,
           '["成绩趋势与多学期对比","薄弱知识点分析","举一反三巩固练习"]' AS c6
    UNION ALL SELECT 'VIP2', '专业会员', 2, 68.00, 668.00,
           '["包含进阶会员全部权益","AI 学情解读报告","学业走势预测与个性化建议"]'
    UNION ALL SELECT 'VIP3', '尊享会员', 3, 128.00, 1288.00,
           '["包含专业会员全部权益","学习报告导出（多子女对比）","优先人工答疑"]'
) t
WHERE NOT EXISTS (SELECT 1 FROM vip_level v WHERE v.level_code = t.c1);

-- 实体继承 BaseEntity（含逻辑删除字段），补上 is_deleted 列
SET @e1 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'xqfx' AND TABLE_NAME = 'vip_level' AND COLUMN_NAME = 'is_deleted');
SET @s1 = IF(@e1 = 0,
    'ALTER TABLE vip_level ADD COLUMN is_deleted tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除''',
    'SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @e2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'xqfx' AND TABLE_NAME = 'user_vip' AND COLUMN_NAME = 'is_deleted');
SET @s2 = IF(@e2 = 0,
    'ALTER TABLE user_vip ADD COLUMN is_deleted tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除''',
    'SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

SELECT level_code, name, level_order, price_month, price_year FROM vip_level ORDER BY level_order;
