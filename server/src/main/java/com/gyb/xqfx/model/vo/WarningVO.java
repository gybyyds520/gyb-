package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警视图对象
 */
@Data
public class WarningVO {

    private Long id;
    private String warningNo;
    private Long studentId;
    private Long termId;
    private Long ruleId;
    private Long predictionId;

    /** 1蓝 2黄 3橙 4红 */
    private Integer warningLevel;

    private String warningType;
    private String triggerDetail;
    private BigDecimal riskProbability;
    private String status;
    private Long ownerUserId;
    private LocalDateTime handleTime;
    private String handleResult;
    private LocalDateTime createTime;

    private String studentNo;
    private String studentName;
    private String clazzName;
    private String phone;
}
