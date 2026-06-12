package io.agentscope.builder.saton.common.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 启动期解析 AES-256 主密钥。优先从环境变量 {@code AGENTSCOPE_BUILDER_SECRET_KEY}
 * 读取 32 字节 base64；缺失时降级到固定开发密钥并打 WARN（仅 dev 用，不要在生产依赖）。
 */
@Component
public class SecretKeyHolder {

    public static final String ENV = "AGENTSCOPE_BUILDER_SECRET_KEY";

    private static final Logger log = LoggerFactory.getLogger(SecretKeyHolder.class);

    private SecretKeySpec key;

    @PostConstruct
    public void init() {
        String env = System.getenv(ENV);
        byte[] raw;
        if (env == null || env.isBlank()) {
            log.warn("============================================================");
            log.warn(" {} not set; falling back to DEV key.", ENV);
            log.warn(" DO NOT USE IN PRODUCTION. Encrypted DB values written now");
            log.warn(" will NOT be readable on a host with a different key.");
            log.warn("============================================================");
            raw = sha256("agentscope-builder-saton-dev-key");
        } else {
            try {
                raw = Base64.getDecoder().decode(env);
            } catch (IllegalArgumentException e) {
                raw = sha256(env);
                log.warn("{} not valid base64; derived AES key via SHA-256.", ENV);
            }
            if (raw.length != 32) {
                raw = sha256(env);
                log.warn("{} decoded length != 32; derived AES key via SHA-256.", ENV);
            }
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public SecretKeySpec key() {
        return key;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
