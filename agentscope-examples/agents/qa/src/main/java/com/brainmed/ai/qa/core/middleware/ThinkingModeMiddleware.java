package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.middleware.enums.ThinkingMode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

/**
 * 思考模式中间件：根据 {@link ThinkingMode}（从 {@link RuntimeContext} 读取）动态追加指令到
 * system prompt，控制模型在回答时的推理深度。
 *
 * <p>覆盖 {@code onSystemPrompt} 钩子，框架通过反射检测重写后自动加入 Transformer 链。
 *
 * <h3>模式行为</h3>
 * <ul>
 *   <li>{@code NORMAL} — 普通模式，快速响应，不做额外约束</li>
 *   <li>{@code DEEP} — 深度思考，引导模型进行更详尽的多步推理</li>
 *   <li>{@code AUTO} — 预留，当前回退为 NORMAL 行为</li>
 * </ul>
 */
@Slf4j
@Order(500)
public class ThinkingModeMiddleware implements MiddlewareBase {

    /** RuntimeContext 中 thinkingMode 的 key，与 QaAgentAdapter / QaChatController 保持一致。 */
    public static final String THINKING_MODE_KEY = "thinkingMode";

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        ThinkingMode mode = resolveThinkingMode(ctx);
        String directive = buildDirective(mode);

        if (directive.isEmpty()) {
            return Mono.just(currentPrompt != null ? currentPrompt : "");
        }

        String base = currentPrompt != null ? currentPrompt : "";
        String separator = base.isEmpty() || base.endsWith("\n") ? "" : "\n";
        String finalPrompt = base + separator + directive;

        log.debug("thinkingMode={}, directive appended to system prompt", mode);
        return Mono.just(finalPrompt);
    }

    /**
     * 根据思考模式构建推理深度指令，追加到 system prompt 末尾。
     * NORMAL 和 AUTO 不追加任何额外指令（默认行为）。
     */
    private String buildDirective(ThinkingMode mode) {
        return switch (mode) {
            case NORMAL, AUTO -> "";
            case DEEP -> """

                    ## 深度思考模式
                    请按以下步骤进行深入推理：
                    1. **分析问题**：识别用户问题的核心意图、隐含前提和关键约束
                    2. **多角度思考**：从不同视角分析，考虑各种可能的情况和例外
                    3. **逻辑推演**：逐步推导，明确每一步的依据，避免跳跃式结论
                    4. **综合判断**：权衡各方面因素，给出有理有据的结论
                    对于医学相关问题，请特别注意区分：已证实的医学共识 vs 尚有争议的观点。
                    回答应全面、有深度，但避免无关的冗余展开。""";
        };
    }

    /**
     * 从 RuntimeContext 安全解析 thinkingMode，默认 NORMAL。
     */
    private ThinkingMode resolveThinkingMode(RuntimeContext ctx) {
        if (ctx == null) {
            return ThinkingMode.NORMAL;
        }
        Object value = ctx.get(THINKING_MODE_KEY);
        if (value instanceof ThinkingMode tm) {
            return tm;
        }
        if (value instanceof String s) {
            return ThinkingMode.fromString(s);
        }
        return ThinkingMode.NORMAL;
    }
}
