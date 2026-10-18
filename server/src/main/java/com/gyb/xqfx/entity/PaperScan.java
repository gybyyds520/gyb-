package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 试卷录入记录（外部图片/PDF/Word → AI 识别 → 题库）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_scan")
public class PaperScan extends BaseEntity {

    /** 试卷标题 */
    private String title;

    /** 关联课程（可选） */
    private Long courseId;

    private String courseName;

    /** 原始文件名 */
    private String fileName;

    /** IMAGE / PDF / WORD */
    private String fileType;

    /** 本地存储路径 */
    private String filePath;

    /** 提取方式 PDF_TEXT / PDF_OCR / DOCX / IMAGE_OCR */
    private String method;

    /** 识别出的全文 */
    private String ocrText;

    /** PENDING待解析 / PARSED已提取 / REVIEWED已入库 / FAILED失败 */
    private String status;

    /** 解析题目数 */
    private Integer questionCount;

    /** AI 试卷分析（知识点分布/难度/点评） */
    private String aiSummary;

    /** AI 评估难度 0-1 */
    private BigDecimal difficulty;

    /** 失败原因 */
    private String errorMsg;

    /** 录入人 sys_user.id */
    private Long creatorId;
}
