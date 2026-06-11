package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;

import java.util.List;
import java.util.UUID;

import reactor.core.publisher.Flux;

/**
 * 测试专用 {@link Model} 实现，永远返回 {@link #CANNED_REPLY}。
 *
 * <p>不发任何外部请求，{@link ReActAgent} 全链路调用能在它身上跑通。
 */
public class TestStubModel implements Model {

    public static final String CANNED_REPLY = "echo: hello";

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        ChatResponse response =
                ChatResponse.builder()
                        .id("msg_" + UUID.randomUUID())
                        .content(List.of(TextBlock.builder().text(CANNED_REPLY).build()))
                        .usage(new ChatUsage(1, 1, 2))
                        .finishReason("stop")
                        .build();
        return Flux.just(response);
    }

    @Override
    public String getModelName() {
        return "test-stub";
    }
}
