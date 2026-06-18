package io.agentscope.builder.saton.factory.service.middleware.impl;

import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.middleware.MiddlewareType;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * SLF4J agent start/result logging middleware. Useful for debugging agent execution.
 */
@Component
public class LoggingMiddlewareType implements MiddlewareType {

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
        return new TypeMeta(type(), "SLF4J 日志", "AgentStart / AgentResult 输出 INFO 日志（无参）", schema);
    }

    @Override
    public MiddlewareBase instantiate(Map<String, Object> props, Path activityDir) {
        return new MiddlewareBase() {
            @Override
            public Flux<AgentEvent> onAgent(
                    Agent agent, RuntimeContext ctx, AgentInput input,
                    Function<AgentInput, Flux<AgentEvent>> next) {
                AGENT_LOG.info("[AgentStart] agent={} inputCount={}",
                        agent.getName(),
                        input.msgs() != null ? input.msgs().size() : 0);
                return next.apply(input)
                        .doOnNext(event -> {
                            if (event instanceof AgentResultEvent result) {
                                AGENT_LOG.info("[AgentResult] agent={} resultRole={}",
                                        agent.getName(),
                                        result.getResult() != null ? result.getResult().getRole() : "?");
                            }
                        });
            }
        };
    }
}
