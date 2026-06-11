package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.hook.HookType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLF4J pre/post call logging hook. Useful for debugging agent execution.
 */
@Component
@SuppressWarnings("deprecation")
public class LoggingHookType implements HookType {

    private static final Logger AGENT_LOG = LoggerFactory.getLogger("io.agentscope.builder.saton.agent");

    @Override
    public String type() {
        return "logging";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return new TypeMeta(type(), "SLF4J 日志", "PreCall / PostCall 输出 INFO 日志（无参）", schema);
    }

    @Override
    public Hook instantiate(Map<String, Object> props, Path activityDir) {
        return new Hook() {
            @Override
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PreCallEvent pre) {
                    AGENT_LOG.info("[PreCall] agent={} inputCount={}",
                            pre.getAgent() != null ? pre.getAgent().getName() : "?",
                            pre.getInputMessages() != null ? pre.getInputMessages().size() : 0);
                } else if (event instanceof PostCallEvent post) {
                    AGENT_LOG.info("[PostCall] agent={} finalMsgRole={}",
                            post.getAgent() != null ? post.getAgent().getName() : "?",
                            post.getFinalMessage() != null ? post.getFinalMessage().getRole() : "?");
                }
                return Mono.just(event);
            }
        };
    }
}
