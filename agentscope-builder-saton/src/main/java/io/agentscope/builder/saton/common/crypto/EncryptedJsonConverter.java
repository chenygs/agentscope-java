package io.agentscope.builder.saton.common.crypto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 把 props 用的 JSON 字符串：
 *  - 写库时 → 对名字命中 {@link SensitiveFields#KEYS} 的字段值做 AES-GCM 加密
 *  - 读库时 → 反向解密
 * 整体 JSON 结构保持不变，仅 value 加密。
 *
 * <p>不在 KEYS 中的 key、null、非字符串值（true/false/数字/对象/数组）一律原样保留。
 */
@Converter
@Component
public class EncryptedJsonConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    /**
     * JPA 通过 SPI 实例化 Converter，但我们也想注入到 Spring；@Autowired + @Lazy
     * 让 Spring 启动期填好。
     */
    @Autowired
    public EncryptedJsonConverter(@Lazy AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plainJson) {
        return transform(plainJson, true);
    }

    @Override
    public String convertToEntityAttribute(String dbJson) {
        return transform(dbJson, false);
    }

    private String transform(String json, boolean encrypt) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(json);
            if (root instanceof ObjectNode obj) {
                walk(obj, encrypt);
                return JsonUtil.mapper().writeValueAsString(obj);
            }
            // 顶层不是 object 就原样回传
            return json;
        } catch (Exception e) {
            throw new IllegalStateException("EncryptedJsonConverter " + (encrypt ? "encrypt" : "decrypt") + " failed", e);
        }
    }

    private void walk(ObjectNode obj, boolean encrypt) {
        var iter = obj.properties().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (val.isObject() && val instanceof ObjectNode child) {
                walk(child, encrypt);
            } else if (val.isString() && SensitiveFields.KEYS.contains(key)) {
                String s = val.asString();
                String out = encrypt ? cipher.encrypt(s) : cipher.decrypt(s);
                obj.put(key, out);
            }
        }
    }
}
