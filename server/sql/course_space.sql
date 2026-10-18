-- 学习通装载第一批：课程空间（章节/资料/讨论/签到/章节测验归属）

CREATE TABLE IF NOT EXISTS `course_chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_offering_id` bigint NOT NULL COMMENT '教学班',
  `chapter_no` int NOT NULL DEFAULT '1' COMMENT '章节序号',
  `title` varchar(128) NOT NULL COMMENT '章节标题',
  `description` text COMMENT '章节说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_chapter_offering` (`course_offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程章节';

CREATE TABLE IF NOT EXISTS `course_material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_offering_id` bigint NOT NULL COMMENT '教学班',
  `chapter_id` bigint DEFAULT NULL COMMENT '所属章节(可空=课程级)',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_type` varchar(32) DEFAULT NULL COMMENT '类型 PDF/WORD/PPT/IMAGE/OTHER',
  `file_size` bigint DEFAULT NULL COMMENT '字节数',
  `file_path` varchar(512) DEFAULT NULL COMMENT '存储路径',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人 sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_material_offering` (`course_offering_id`),
  KEY `idx_material_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程资料';

CREATE TABLE IF NOT EXISTS `course_discussion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_offering_id` bigint NOT NULL COMMENT '教学班',
  `author_id` bigint DEFAULT NULL COMMENT '作者 sys_user.id',
  `author_name` varchar(64) DEFAULT NULL COMMENT '作者显示名',
  `author_type` varchar(16) DEFAULT NULL COMMENT 'TEACHER/STUDENT',
  `content` text NOT NULL COMMENT '话题内容',
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '置顶 0/1',
  `like_count` int NOT NULL DEFAULT '0',
  `reply_count` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_disc_offering` (`course_offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程讨论帖';

CREATE TABLE IF NOT EXISTS `course_discussion_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `discussion_id` bigint NOT NULL COMMENT '帖子',
  `author_id` bigint DEFAULT NULL,
  `author_name` varchar(64) DEFAULT NULL,
  `author_type` varchar(16) DEFAULT NULL,
  `content` text NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_reply_disc` (`discussion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程讨论回复';

ALTER TABLE `homework` ADD COLUMN `course_offering_id` bigint DEFAULT NULL COMMENT '归属教学班(章节测验)' AFTER `course_id`;
