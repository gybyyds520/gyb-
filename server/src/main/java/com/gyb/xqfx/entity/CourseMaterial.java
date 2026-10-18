package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 课程资料 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_material")
public class CourseMaterial extends BaseEntity {

    private Long courseOfferingId;

    private Long chapterId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String filePath;

    private Long uploaderId;
}
