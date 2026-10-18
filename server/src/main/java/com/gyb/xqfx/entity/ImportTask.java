package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据导入任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_task")
public class ImportTask extends BaseEntity {

    /** 任务编号 */
    private String taskNo;

    /** STUDENT / SCORE / ATTENDANCE / PERFORMANCE / BEHAVIOR */
    private String importType;

    private String fileName;

    private String fileUrl;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    /** RUNNING / SUCCESS / FAILED / PARTIAL */
    private String status;

    private String errorReportUrl;

    private Long operatorId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
