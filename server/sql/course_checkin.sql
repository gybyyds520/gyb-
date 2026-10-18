CREATE TABLE IF NOT EXISTS `course_checkin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_offering_id` bigint NOT NULL COMMENT '教学班',
  `teacher_id` bigint DEFAULT NULL COMMENT '发起教师',
  `code` varchar(8) NOT NULL COMMENT '6位签到码',
  `title` varchar(128) DEFAULT NULL COMMENT '签到标题',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE进行中/CLOSED已结束',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_checkin_offering` (`course_offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课堂签到活动';
