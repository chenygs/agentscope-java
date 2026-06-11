package io.agentscope.builder.saton.common.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 进程级 ObjectMapper 单例（基于 Spring Boot 4 默认的 Jackson 3）。
 * 注意：包名是 {@code tools.jackson.databind}，不是 {@code com.fasterxml.jackson.databind}。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private JsonUtil() {}
}
