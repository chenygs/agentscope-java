package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class AuditJsonlHookTypeTest {

    @TempDir Path activityDir;

    @Test
    void postCallAppendsJsonlLine() throws Exception {
        AuditJsonlHookType type = new AuditJsonlHookType();
        Hook hook = type.instantiate(Map.of(), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("test-agent");

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("hello").build()).build();
        PostCallEvent event = new PostCallEvent(dummyAgent, msg);
        hook.onEvent(event).block();

        Path jsonl = activityDir.resolve("activity").resolve("activity.jsonl");
        assertTrue(Files.exists(jsonl), "jsonl file should exist at " + jsonl);
        List<String> lines = Files.readAllLines(jsonl);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"agent\":\"test-agent\""), "line should contain agent name; got: " + lines.get(0));
    }

    @Test
    void customLogDirIsRespected() throws Exception {
        AuditJsonlHookType type = new AuditJsonlHookType();
        Hook hook = type.instantiate(Map.of("logDir", "audit"), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("a");

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("x").build()).build();
        hook.onEvent(new PostCallEvent(dummyAgent, msg)).block();

        assertTrue(Files.exists(activityDir.resolve("audit").resolve("activity.jsonl")));
    }
}
