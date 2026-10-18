package com.gyb.xqfx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基于 Ollama 大模型的高校学情预测分析系统 —— 后端启动类
 */
@EnableScheduling
@MapperScan("com.gyb.xqfx.mapper")
@SpringBootApplication
public class XqfxApplication {

    public static void main(String[] args) {
        SpringApplication.run(XqfxApplication.class, args);
    }
}
