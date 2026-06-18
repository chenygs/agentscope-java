package io.agentscope.builder.saton.factory.service.middleware.impl;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.middleware.MiddlewareType;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Append-only JSONL audit middleware. Observes {@link AgentResultEvent} in the agent
 * event stream and serializes the result message as one line to
 * {@code <activityDir>/<logDir>/activity.jsonl}; {@code logDir} defaults to {@code "activity"}.
 *
 * <p>Designed for audit workflow — UI lists this file via /api/agents/{id}/activity.
 */
@Component
@Slf4j
public class AuditJsonlMiddlewareType implements MiddlewareType {

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
        return new TypeMeta(type(), "JSONL 审计", "AgentResult 追加到 activity.jsonl", schema);
    }

    @Override
    public MiddlewareBase instantiate(Map<String, Object> props, Path activityDir) {
        String subdir = optionalString(props, "logDir", DEFAULT_LOG_DIR);
        Path targetDir = activityDir.resolve(subdir).normalize();
        Path target = targetDir.resolve(JSONL_FILE);

        return new MiddlewareBase() {
            @Override
            public Flux<AgentEvent> onAgent(
                    Agent agent, RuntimeContext ctx, AgentInput input,
                    Function<AgentInput, Flux<AgentEvent>> next) {
                return next.apply(input)
                        .doOnNext(event -> {
                            if (event instanceof AgentResultEvent result) {
                                try {
                                    Files.createDirectories(targetDir);
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    entry.put("ts", System.currentTimeMillis());
                                    entry.put("agent", agent.getName());
                                    entry.put("finalMsg", result.getResult());
                                    String line = JsonUtil.mapper().writeValueAsString(entry)
                                            + System.lineSeparator();
                                    Files.writeString(target, line, StandardCharsets.UTF_8,
                                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                } catch (IOException e) {
                                    log.warn("audit-jsonl write failed: {}", e.getMessage());
                                }
                            }
                        });
            }
        };
    }

    private static String optionalString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object v = props.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
