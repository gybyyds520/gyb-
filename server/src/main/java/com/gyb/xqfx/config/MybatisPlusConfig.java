package com.gyb.xqfx.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis-Plus 配置：分页插件 + 批量插入注入器
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 为所有 Mapper 注入 insertBatchSomeColumn 批量插入方法，
     * 避免大批量落库时逐条 insert 造成的性能问题。
     */
    @Bean
    public DefaultSqlInjector batchInsertSqlInjector() {
        return new DefaultSqlInjector() {
            @Override
            public List<AbstractMethod> getMethodList(org.apache.ibatis.session.Configuration configuration,
                                                      Class<?> mapperClass,
                                                      com.baomidou.mybatisplus.core.metadata.TableInfo tableInfo) {
                List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
                methods.add(new InsertBatchSomeColumn());
                return methods;
            }
        };
    }
}
