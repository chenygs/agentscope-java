package io.agentscope.builder.saton.agent.chat;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentAccessGuard;
import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.Tier;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatService {

    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(2);
    private static final String DEFAULT_SESSION_KEY = "default";

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentRuntimeResolver runtimeResolver;
    private final AgentAccessGuard accessGuard;

    public ChatService(AgentDefinitionRepository agentRepo,
                       ModelProviderRepository modelRepo,
                       AgentRuntimeResolver runtimeResolver,
                       AgentAccessGuard accessGuard) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
        this.accessGuard = accessGuard;
    }

    public ChatSendResp send(Long agentDefId, ChatSendReq req) {
        String me = StpUtil.getLoginIdAsString();
        AgentDefinitionEntity def = accessGuard.require(agentDefId, me, Tier.RUN);
        Long effectiveModelId = req.overrideModelProviderId() != null
                ? req.overrideModelProviderId()
                : def.getDefaultModelProviderId();
        if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + effectiveModelId);
        }
        HarnessAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId, me);

        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(req.message() == null ? "" : req.message()).build())
                .build();

        RuntimeContext ctx = buildContext(me, def.getId(), req.sessionKey());
        Msg reply = agent.call(java.util.List.of(userMsg), ctx).block(CALL_TIMEOUT);
        return new ChatSendResp(extractText(reply), def.getId(), effectiveModelId);
    }

    public reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent> stream(
            Long agentDefId,
            ChatSendReq req) {
        // spec §12.14 — capture loginId BEFORE Flux.defer (sa-token context gone by inner subscribe)
        String me = StpUtil.getLoginIdAsString();
        return reactor.core.publisher.Flux.defer(() -> {
            AgentDefinitionEntity def = accessGuard.require(agentDefId, me, Tier.RUN);
            Long effectiveModelId = req.overrideModelProviderId() != null
                    ? req.overrideModelProviderId()
                    : def.getDefaultModelProviderId();
            if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
                throw new NotFoundException(
                        "model provider not found or not yours: " + effectiveModelId);
            }
            HarnessAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId, me);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder()
                            .text(req.message() == null ? "" : req.message()).build())
                    .build();

            RuntimeContext ctx = buildContext(me, def.getId(), req.sessionKey());
            return agent.streamEvents(userMsg, ctx);
        });
    }

    private static RuntimeContext buildContext(String userId, Long agentDefId, String sessionKey) {
        String key = (sessionKey == null || sessionKey.isBlank()) ? DEFAULT_SESSION_KEY : sessionKey;
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId("agent_" + agentDefId + "_" + key)
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock t) {
                if (t.getText() != null) sb.append(t.getText());
            }
        }
        return sb.toString();
    }
}
