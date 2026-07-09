package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.middleware.enums.SearchMode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

/**
 * 搜索模式中间件：根据 {@link SearchMode}（从 {@link RuntimeContext} 读取）动态追加约束指令到
 * system prompt，控制模型在 ReAct 循环中可以使用哪些搜索工具。
 *
 * <p>覆盖 {@code onSystemPrompt} 钩子，框架通过反射检测重写后自动加入 Transformer 链。
 * 不修改任何其他钩子（onReasoning / onAgent / onModelCall / onActing），保持零侵入。
 *
 * <h3>模式约束规则</h3>
 * <ul>
 *   <li>{@code AUTO} — 你有 web_search_v2 和 knowledge_search_v2 两个搜索工具，每次对话最多只允许调用其中一个</li>
 *   <li>{@code WEB} — 本次对话只允许使用 web_search_v2 工具进行联网搜索，禁止使用 knowledge_search_v2</li>
 *   <li>{@code KNOWLEDGE} — 本次对话只允许使用 knowledge_search_v2 工具搜索知识库，禁止使用 web_search_v2</li>
 *   <li>{@code NONE} — 本次对话禁止使用任何搜索工具，请直接基于已有知识回答</li>
 * </ul>
 */
@Slf4j
@Order(400)
public class SearchModeMiddleware implements MiddlewareBase {

    /** RuntimeContext 中 searchMode 的 key，与 QaAgentAdapter / Tool 保持一致。 */
    public static final String SEARCH_MODE_KEY = "searchMode";

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        SearchMode mode = resolveSearchMode(ctx);
        String constraint = buildConstraint(mode);

        if (constraint.isEmpty()) {
            return Mono.just(currentPrompt != null ? currentPrompt : "");
        }

        String base = currentPrompt != null ? currentPrompt : "";
        String separator = base.isEmpty() || base.endsWith("\n") ? "" : "\n";
        String finalPrompt = base + separator + constraint;

        log.debug("searchMode={}, constraint appended to system prompt", mode);
        return Mono.just(finalPrompt);
    }

    /**
     * 根据搜索模式构建约束指令文本，追加到 system prompt 末尾。
     */
    private String buildConstraint(SearchMode mode) {
        return switch (mode) {
            case AUTO -> """

                    ## 搜索工具使用指引
                    你拥有以下搜索工具：
                    - **web_search_v2**：联网搜索，获取实时互联网信息
                    - **knowledge_search_v2**：知识库搜索，检索私域专业文档
                    **重要规则**：每次对话最多只允许调用**一个**搜索工具。根据用户问题选择最合适的一个：
                    - 医学/专业相关知识 → 优先用 knowledge_search_v2
                    - 实时资讯/公开信息 → 用 web_search_v2
                    - 打招呼、闲聊等不需要搜索的问题，请直接回答，不要调用搜索工具。
                    选定一个工具后，不要再调用另一个搜索工具。""";

            case WEB -> """

                    ## 搜索工具使用指引
                    本次对话**只允许使用 web_search_v2** 工具进行联网搜索。
                    **禁止使用 knowledge_search_v2** 工具。
                    如果用户的问题需要实时信息或最新资讯，请调用 web_search_v2。""";

            case KNOWLEDGE -> """

                    ## 搜索工具使用指引
                    本次对话**只允许使用 knowledge_search_v2** 工具搜索私域知识库。
                    **禁止使用 web_search_v2** 工具。
                    如果用户的问题涉及专业领域知识，请调用 knowledge_search_v2。""";

            case NONE -> """

                    ## 搜索工具使用指引
                    本次对话**禁止使用任何搜索工具**（包括 web_search_v2 和 knowledge_search_v2）。
                    请直接基于已有知识回答用户问题。""";
        };
    }

    /**
     * 从 RuntimeContext 安全解析 searchMode，默认 AUTO。
     */
    private SearchMode resolveSearchMode(RuntimeContext ctx) {
        if (ctx == null) {
            return SearchMode.AUTO;
        }
        Object value = ctx.get(SEARCH_MODE_KEY);
        if (value instanceof SearchMode sm) {
            return sm;
        }
        if (value instanceof String s) {
            return SearchMode.fromString(s);
        }
        return SearchMode.AUTO;
    }
}
