package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 课程讨论帖 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_discussion")
public class CourseDiscussion extends BaseEntity {

    private Long courseOfferingId;

    private Long authorId;

    private String authorName;

    /** TEACHER / STUDENT */
    private String authorType;

    private String content;

    private Integer pinned = 0;

    private Integer likeCount = 0;

    private Integer replyCount = 0;
}
