package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 课程讨论回复 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_discussion_reply")
public class CourseDiscussionReply extends BaseEntity {

    private Long discussionId;

    private Long authorId;

    private String authorName;

    /** TEACHER / STUDENT */
    private String authorType;

    private String content;
}
