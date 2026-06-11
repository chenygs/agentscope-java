package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.tool.ToolType;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 测试 only ToolType，type 名 "tool-stub"。仅用于验证：
 * <ul>
 *   <li>ToolFactory + AgentBuildOrchestrator 在测试上下文里能装配带工具的 agent</li>
 *   <li>带 tool 的 agent 调 streamEvents 不会因为 tool 注册失败而崩</li>
 * </ul>
 *
 * <p>本 stub 不真正触发 tool call（stub model 不会输出 ToolUseBlock）；它只验
 * "注册成功 + stream 完成"。用 POJO + {@link Tool} 路线，回避 {@code AgentTool}
 * 接口 6 个方法的样板。
 */
@Component
public class StubToolType implements ToolType {

    @Override
    public String type() {
        return "tool-stub";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "tool-stub",
                "Stub Tool",
                "test-only stub; returns a canned text result if ever called",
                JsonSchemaUtil.object().build()
        );
    }

    @Override
    public Object instantiate(Map<String, Object> props) {
        return new StubToolPojo();
    }

    /** Inner POJO with one @Tool method; Toolkit scans annotated methods. */
    public static class StubToolPojo {
        @Tool(name = "stub_tool", description = "test-only stub; returns a canned text result")
        public Mono<ToolResultBlock> call() {
            return Mono.just(ToolResultBlock.text("stub tool result"));
        }
    }
}
