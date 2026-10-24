package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成长激励卡生成记录（历史激励卡相册）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("motivation_card")
public class MotivationRecord extends BaseEntity {

    /** 学生ID */
    private Long studentId;

    /** 学生姓名（冗余，便于列表展示） */
    private String studentName;

    /** 风格：ACADEMIC / STAR / NATURE */
    private String style;

    /** 生成提示词 */
    private String prompt;

    /** 图片相对路径，如 /api/motivation/image/202610/xxx.png */
    private String imagePath;
}
