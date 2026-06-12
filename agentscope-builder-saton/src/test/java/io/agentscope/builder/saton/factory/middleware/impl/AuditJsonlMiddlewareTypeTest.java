package io.agentscope.builder.saton.factory.middleware.impl;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuditJsonlMiddlewareTypeTest {

    @TempDir Path activityDir;

    @Test
    void agentResultAppendsJsonlLine() throws Exception {
        AuditJsonlMiddlewareType type = new AuditJsonlMiddlewareType();
        MiddlewareBase mw = type.instantiate(Map.of(), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("test-agent");
        RuntimeContext ctx = Mockito.mock(RuntimeContext.class);

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("hello").build()).build();
        AgentResultEvent resultEvent = new AgentResultEvent(msg);

        AgentInput input = new AgentInput(List.of());
        Function<AgentInput, Flux<AgentEvent>> next = in -> Flux.just(resultEvent);

        mw.onAgent(dummyAgent, ctx, input, next).collectList().block();

        Path jsonl = activityDir.resolve("activity").resolve("activity.jsonl");
        assertTrue(Files.exists(jsonl), "jsonl file should exist at " + jsonl);
        List<String> lines = Files.readAllLines(jsonl);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"agent\":\"test-agent\""), "line should contain agent name; got: " + lines.get(0));
    }

    @Test
    void customLogDirIsRespected() throws Exception {
        AuditJsonlMiddlewareType type = new AuditJsonlMiddlewareType();
        MiddlewareBase mw = type.instantiate(Map.of("logDir", "audit"), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("a");
        RuntimeContext ctx = Mockito.mock(RuntimeContext.class);

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("x").build()).build();
        AgentResultEvent resultEvent = new AgentResultEvent(msg);

        AgentInput input = new AgentInput(List.of());
        Function<AgentInput, Flux<AgentEvent>> next = in -> Flux.just(resultEvent);

        mw.onAgent(dummyAgent, ctx, input, next).collectList().block();

        assertTrue(Files.exists(activityDir.resolve("audit").resolve("activity.jsonl")));
    }
}
