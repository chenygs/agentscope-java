package io.agentscope.builder.saton.common.crypto;

import java.util.Set;

/**
 * JSON props 里需要字段级加密的 key 名（大小写敏感、原样匹配）。
 * 加新资源时如果命名不同（比如 webhookSecret）记得追加。
 */
public final class SensitiveFields {

    public static final Set<String> KEYS = Set.of(
            "apiKey",
            "token",
            "password",
            "secret",
            "accessKeySecret"
    );

    public static final String MASKED_VALUE = "***";

    private SensitiveFields() {}
}
