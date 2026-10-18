package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 特征记录（模型输入快照，保证预测可追溯）
 */
@Data
@TableName("feature_record")
public class FeatureRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long termId;

    private Long courseId;

    /** 特征 JSON */
    private String featureJson;

    /** 标签值（训练样本才有） */
    private BigDecimal labelValue;

    private LocalDateTime createTime;

    private Integer isDeleted;
}
