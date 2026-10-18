-- 学生选课关系（课程空间/签到按选课归属；跨班选修与行政班并存）
CREATE TABLE IF NOT EXISTS `course_enrollment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_offering_id` bigint NOT NULL COMMENT '教学班',
  `student_id` bigint NOT NULL COMMENT '学生',
  `enroll_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_enroll_student` (`student_id`),
  KEY `idx_enroll_offering` (`course_offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生选课关系';

-- 回填模拟选课：每名学生确定性选约 3 门课（演示数据）
INSERT INTO course_enrollment (course_offering_id, student_id)
SELECT o.id, s.id
FROM student s
JOIN course_offering o ON o.is_deleted = 0
WHERE s.status = 'ENROLLED'
AND (s.id * 3 + o.id) % 7 < 3;
