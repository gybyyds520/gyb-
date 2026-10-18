package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长回执：家长对学校预警与帮扶措施的确认与反馈
 */
@Data
@TableName("parent_feedback")
public class ParentFeedback {

    private Long id;

    private Long studentId;

    /** 家长账号ID */
    private Long parentId;

    /** 业务类型: WARNING预警 / INTERVENTION帮扶 */
    private String bizType;

    private Long bizId;

    /** ACKNOWLEDGED已阅 / SUPPORTED配合 / OBJECTED有异议 */
    private String ackStatus;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}
