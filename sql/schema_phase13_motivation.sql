-- ============================================================
-- 成长激励卡生成记录（历史激励卡相册）
-- 学生生成激励卡后落库，支持学生个人相册 / 教师家长按数据范围查询 / 管理员全量监察
-- ============================================================
CREATE TABLE IF NOT EXISTS `motivation_card` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `student_id`   BIGINT       NOT NULL COMMENT '学生ID',
  `student_name` VARCHAR(50)  NOT NULL COMMENT '学生姓名（冗余）',
  `style`        VARCHAR(20)  NOT NULL DEFAULT 'ACADEMIC' COMMENT '风格：ACADEMIC/STAR/NATURE',
  `prompt`       VARCHAR(1000) DEFAULT NULL COMMENT '生成提示词',
  `image_path`   VARCHAR(255) NOT NULL COMMENT '图片相对路径 /api/motivation/image/yyyyMM/xxx.png',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_motivation_student_time` (`student_id`, `create_time`)
) ENGINE = InnoDB COMMENT = '成长激励卡生成记录表';
