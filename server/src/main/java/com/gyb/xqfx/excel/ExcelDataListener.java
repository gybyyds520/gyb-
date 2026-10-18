package com.gyb.xqfx.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * EasyExcel 通用分批读取监听器
 */
@Slf4j
public class ExcelDataListener<T> extends AnalysisEventListener<T> {

    private static final int BATCH_SIZE = 500;

    private final Consumer<List<T>> batchConsumer;
    private final List<T> cache = new ArrayList<>(BATCH_SIZE);
    private int totalCount;

    public ExcelDataListener(Consumer<List<T>> batchConsumer) {
        this.batchConsumer = batchConsumer;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        if (data == null) {
            return;
        }
        totalCount++;
        cache.add(data);
        if (cache.size() >= BATCH_SIZE) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        log.warn("Excel 解析异常，行号 {}：{}",
                context.readRowHolder() == null ? -1 : context.readRowHolder().getRowIndex(),
                exception.getMessage());
    }

    private void flush() {
        if (cache.isEmpty()) {
            return;
        }
        batchConsumer.accept(new ArrayList<>(cache));
        cache.clear();
    }

    public int getTotalCount() {
        return totalCount;
    }
}
