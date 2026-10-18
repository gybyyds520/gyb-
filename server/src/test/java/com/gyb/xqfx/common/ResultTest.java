package com.gyb.xqfx.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 单元测试（示例：参数化驱动，用于快速积累用例数量）
 */
@DisplayName("统一返回结构 Result 测试")
class ResultTest {

    @Test
    @DisplayName("success 应返回 200 与默认提示")
    void testSuccessDefault() {
        Result<String> result = Result.success();
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
        assertTrue(result.isSuccess());
    }

    @ParameterizedTest(name = "success 携带数据 #{index}: {0}")
    @ValueSource(strings = {"hello", "", "学情数据", "123"})
    @DisplayName("success 携带不同类型数据均应成功")
    void testSuccessWithData(String data) {
        Result<String> result = Result.success(data);
        assertEquals(200, result.getCode());
        assertEquals(data, result.getData());
        assertTrue(result.isSuccess());
    }

    @ParameterizedTest(name = "自定义成功消息 #{index}")
    @CsvSource({
            "导入成功, 100",
            "预测完成, 200",
            "预警已生成, 300"
    })
    @DisplayName("success 自定义消息与业务数据")
    void testSuccessWithMessage(String message, Integer bizId) {
        Result<Integer> result = Result.success(bizId, message);
        assertEquals(message, result.getMessage());
        assertEquals(bizId, result.getData());
    }

    @ParameterizedTest(name = "错误码 {0} 应保持码值一致")
    @EnumSource(value = ErrorCode.class, names = {"SUCCESS"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("所有错误码构造异常均正确")
    void testFailByErrorCode(ErrorCode errorCode) {
        Result<Void> result = Result.fail(errorCode);
        assertEquals(errorCode.getCode(), result.getCode());
        assertEquals(errorCode.getMessage(), result.getMessage());
        assertFalse(result.isSuccess());
    }

    @ParameterizedTest(name = "边界码值 {0} -> {1}")
    @CsvSource({
            "0, 0",
            "-1, -1",
            "999999, 999999"
    })
    @DisplayName("自定义错误码原样返回")
    void testFailWithCustomCode(int code, int expected) {
        Result<Void> result = Result.fail(code, "自定义错误");
        assertEquals(expected, result.getCode());
        assertEquals("自定义错误", result.getMessage());
    }

    @Test
    @DisplayName("时间戳应大于 0")
    void testTimestamp() {
        assertTrue(Result.success().getTimestamp() > 0);
    }

    @Test
    @DisplayName("业务异常应携带错误码")
    void testBusinessException() {
        BusinessException e = new BusinessException(ErrorCode.ML_SERVICE_ERROR, "模型服务超时");
        assertEquals(701, e.getCode());
        assertEquals("模型服务超时", e.getMessage());
    }
}
