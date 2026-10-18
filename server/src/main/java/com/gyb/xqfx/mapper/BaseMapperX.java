package com.gyb.xqfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * 扩展 Mapper：提供真正的批量插入（单条 SQL 多值），由
 * {@code InsertBatchSomeColumn} 注入实现。
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    /**
     * 批量插入，实体中 null 字段不参与 SQL 拼接
     */
    int insertBatchSomeColumn(List<T> entityList);
}
