package com.gyb.xqfx.config;

import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动实时初始化：检测核心数据表，为空时自动生成演示数据。
 * <p>
 * 保证项目启动后所有页面（大屏/预警/画像/目标/待办等）立即处于
 * 可展示的实时状态，满足毕设演示"启动即有数据"的要求。
 * 可通过 {@code xqfx.bootstrap.auto-demo} 关闭。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataBootstrapRunner implements ApplicationRunner {

    private final StudentMapper studentMapper;
    private final DemoDataService demoDataService;

    @Value("${xqfx.bootstrap.auto-demo:true}")
    private boolean autoDemo;

    @Value("${xqfx.bootstrap.demo-count:200}")
    private int demoCount;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoDemo) {
            log.info("启动初始化已关闭（xqfx.bootstrap.auto-demo=false）");
            return;
        }
        Long count = studentMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("检测到已有学生数据 {} 条，跳过自动初始化，系统以现有数据实时运行", count);
            return;
        }
        Map<String, Object> summary = demoDataService.generate(demoCount);
        log.info("启动自动初始化完成：演示数据已生成，各表条数 {}", summary);
    }
}
