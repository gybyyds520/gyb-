package com.gyb.xqfx.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    VIP_REQUIRED(402, "该功能需要开通会员"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(500, "业务处理失败"),
    DATA_QUALITY_ERROR(601, "数据质量校验未通过"),
    IMPORT_ERROR(602, "数据导入失败"),
    ML_SERVICE_ERROR(701, "预测模型服务异常"),
    AI_SERVICE_ERROR(702, "大模型服务异常"),
    AI_FALLBACK(703, "大模型调用失败，已降级处理"),
    SYSTEM_ERROR(999, "系统内部错误");

    private final int code;
    private final String message;
}
