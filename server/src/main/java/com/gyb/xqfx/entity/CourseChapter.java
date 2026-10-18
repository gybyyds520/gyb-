package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 课程章节 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_chapter")
public class CourseChapter extends BaseEntity {

    private Long courseOfferingId;

    private Integer chapterNo;

    private String title;

    private String description;
}
