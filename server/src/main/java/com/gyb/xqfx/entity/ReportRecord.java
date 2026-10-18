package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生成的报告记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("report_record")
public class ReportRecord extends BaseEntity {

    /** STUDENT / CLASS / COURSE / TERM */
    private String reportType;

    private Long bizId;

    private Long termId;

    private String modelName;

    private String fileUrl;

    /** HTML / PDF / DOCX */
    private String fileFormat;

    /** GENERATING / SUCCESS / FAILED */
    private String status;

    private Long generatorId;
}
