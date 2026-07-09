package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.tool.SynonymDictionary;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 查询重写中间件。
 * <p>
 * 在模型推理前，找到最后一条用户消息，使用同义词词典扩展其中的专业术语，
 * 使模型能更好地理解用户意图并生成更精准的搜索查询。
 * <p>
 * 例如：用户问 "GBM是什么" → 模型看到的消息变为
 * "GBM是什么（相关术语：胶质母细胞瘤, 胶母细胞瘤）"
 *
 * @author Qoder
 */
@Order(100)
public class QueryExpansionMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(QueryExpansionMiddleware.class);

    private final SynonymDictionary synonymDictionary;

    public QueryExpansionMiddleware(SynonymDictionary synonymDictionary) {
        this.synonymDictionary = synonymDictionary;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        List<Msg> messages = input.messages();

        // 从后往前找最后一条真正的用户消息（跳过长期记忆注入的消息）
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (msg.getRole() == MsgRole.USER
                    && !"long_term_memory".equals(msg.getName())) {
                lastUserIdx = i;
                break;
            }
        }

        if (lastUserIdx < 0) {
            return next.apply(input);
        }

        Msg userMsg = messages.get(lastUserIdx);
        String originalText = userMsg.getTextContent();
        if (originalText.isBlank()) {
            return next.apply(input);
        }

        String expandedText = synonymDictionary.expandQuery(originalText);
        if (expandedText.equals(originalText)) {
            return next.apply(input);
        }

        log.info("[QueryRewrite] 用户消息重写: '{}' -> '{}'", originalText, expandedText);

        // 用扩展后的文本替换原 TextBlock
        List<ContentBlock> newContent = new ArrayList<>();
        for (ContentBlock block : userMsg.getContent()) {
            if (block instanceof TextBlock) {
                newContent.add(TextBlock.builder().text(expandedText).build());
            } else {
                newContent.add(block);
            }
        }

        Msg rewrittenMsg = userMsg.withContent(newContent);
        List<Msg> newMessages = new ArrayList<>(messages);
        newMessages.set(lastUserIdx, rewrittenMsg);

        return next.apply(new ReasoningInput(newMessages, input.tools(), input.options()));
    }
}
