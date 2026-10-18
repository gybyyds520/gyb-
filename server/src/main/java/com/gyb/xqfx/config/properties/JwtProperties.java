package com.gyb.xqfx.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xqfx.jwt")
public class JwtProperties {

    /** 签名密钥 */
    private String secret;

    /** access token 有效期(ms) */
    private long expiration = 7200000L;

    /** refresh token 有效期(ms) */
    private long refreshExpiration = 604800000L;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀 */
    private String prefix = "Bearer ";
}
