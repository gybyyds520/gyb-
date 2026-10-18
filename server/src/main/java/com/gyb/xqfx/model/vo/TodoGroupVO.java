package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 待办中心分组
 */
@Data
public class TodoGroupVO {

    /** 分组编码：WARNING预警 / TICKET工单 / FEEDBACK反馈 / GOAL目标 / NOTIFY通知 */
    private String code;

    private String title;

    /** 待办数量 */
    private Integer count;

    /** 待办明细 */
    private List<TodoItemVO> items;

    @Data
    public static class TodoItemVO {
        private Long id;
        private String title;
        private String summary;
        /** 时间（ISO 或格式化字符串） */
        private String time;
        /** 严重度/优先级：SEVERE/ATTENTION/NORMAL 或 1-4 级 */
        private String level;
        private String status;
        /** 前端跳转路径 */
        private String actionPath;
    }
}
