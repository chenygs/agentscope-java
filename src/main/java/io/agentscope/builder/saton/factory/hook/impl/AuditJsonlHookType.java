package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.hook.HookType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only JSONL audit hook. Each PostCallEvent's final message is serialized as one line
 * to {@code <activityDir>/<logDir>/activity.jsonl}; {@code logDir} defaults to {@code "activity"}.
 *
 * <p>Designed for M10 audit workflow — UI lists this file via /api/agents/{id}/activity.
 */
@Component
@SuppressWarnings("deprecation")
public class AuditJsonlHookType implements HookType {

    private static final Logger log = LoggerFactory.getLogger(AuditJsonlHookType.class);
    private static final String DEFAULT_LOG_DIR = "activity";
    private static final String JSONL_FILE = "activity.jsonl";

    @Override
    public String type() {
        return "audit-jsonl";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> logDir = new LinkedHashMap<>();
        logDir.put("type", "string");
        logDir.put("description", "log 子目录（相对 activityDir，默认 activity）");
        properties.put("logDir", logDir);
        schema.put("properties", properties);
        return new TypeMeta(type(), "JSONL 审计", "PostCall 把 final msg 追加到 activity.jsonl", schema);
    }

    @Override
    public Hook instantiate(Map<String, Object> props, Path activityDir) {
        String subdir = optionalString(props, "logDir", DEFAULT_LOG_DIR);
        Path targetDir = activityDir.resolve(subdir).normalize();
        Path target = targetDir.resolve(JSONL_FILE);

        return new Hook() {
            @Override
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PostCallEvent post) {
                    try {
                        Files.createDirectories(targetDir);
                        String agentName = post.getAgent() != null ? post.getAgent().getName() : "?";
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("ts", System.currentTimeMillis());
                        entry.put("agent", agentName);
                        entry.put("finalMsg", post.getFinalMessage());
                        String line = JsonUtil.mapper().writeValueAsString(entry) + System.lineSeparator();
                        Files.writeString(target, line, StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        log.warn("audit-jsonl write failed: {}", e.getMessage());
                    }
                }
                return Mono.just(event);
            }
        };
    }

    private static String optionalString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object v = props.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
