package io.agentscope.builder.saton.agent.chat;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatService {

    /** 同步 send 最大等待时间。超时 = 抛 RuntimeException。 */
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(2);

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentRuntimeResolver runtimeResolver;

    public ChatService(AgentDefinitionRepository agentRepo,
                       ModelProviderRepository modelRepo,
                       AgentRuntimeResolver runtimeResolver) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
    }

    public ChatSendResp send(Long agentDefId, ChatSendReq req) {
        String me = StpUtil.getLoginIdAsString();

        AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, me)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));

        Long effectiveModelId = req.overrideModelProviderId() != null
                ? req.overrideModelProviderId()
                : def.getDefaultModelProviderId();

        // 校验 override 模型也属于当前 owner
        if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + effectiveModelId);
        }

        ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);

        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(req.message() == null ? "" : req.message()).build())
                .build();

        Msg reply = agent.call(userMsg).block(CALL_TIMEOUT);
        String text = extractText(reply);
        return new ChatSendResp(text, def.getId(), effectiveModelId);
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock t) {
                if (t.getText() != null) sb.append(t.getText());
            }
            // skip ThinkingBlock / ToolUseBlock / etc. — only surface TextBlock to the user
        }
        return sb.toString();
    }
}
