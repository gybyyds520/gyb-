package com.gyb.xqfx.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页返回结构
 */
@Data
public class PageResult<T> {

    private long total;
    private long pageSize;
    private long current;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageSize(page.getSize());
        result.setCurrent(page.getCurrent());
        result.setRecords(page.getRecords());
        return result;
    }

    public static <S, T> PageResult<T> of(IPage<S> page, Function<S, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageSize(page.getSize());
        result.setCurrent(page.getCurrent());
        result.setRecords(page.getRecords().stream().map(converter).collect(Collectors.toList()));
        return result;
    }
}
