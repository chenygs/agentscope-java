package com.brainmed.ai.qa.core.memory;

import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 AgentScope SDK 的 Mem0 长期记忆提供者。
 *
 * <p>使用 {@link Mem0LongTermMemory} 封装的 retrieve/record API。
 * 支持 {@code apiType}（platform / self_hosted）切换认证方式。
 *
 * <p><b>已知限制：</b>
 * <ul>
 *   <li>{@code retrieve()} 内部使用 {@code onErrorReturn("")} 静默吞掉所有异常，
 *       排查连接/鉴权问题时无日志可看，需切 HTTP 模式排查。</li>
 *   <li>{@code record()} 只接受 {@code List<Msg>}（user 角色），无法传 assistant 回复，
 *       Mem0 抽取引擎可能把闲聊也当记忆存储。</li>
 * </ul>
 *
 * @see Mem0MemoryProvider HTTP 直连版（日志完整，支持 user+assistant 完整写入）
 */
public class Mem0SdkMemoryProvider implements LongTermMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(Mem0SdkMemoryProvider.class);

    private final String apiKey;
    private final String apiBaseUrl;
    private final Mem0ApiType apiType;

    /**
     * @param apiKey      Mem0 API Key
     * @param apiBaseUrl  Mem0 服务端点
     * @param apiTypeStr  API 类型字符串：platform | self_hosted
     */
    public Mem0SdkMemoryProvider(String apiKey, String apiBaseUrl, Mem0ApiType apiType) {
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
        this.apiType = apiType;
        log.info("[Mem0-SDK] 初始化: apiBaseUrl={}, apiType={}", apiBaseUrl, this.apiType);
    }

    @Override
    public Mono<String> search(Msg userQuery, String userId, String agentId) {
        Mem0LongTermMemory memory = buildMemory(userId, agentId);
        log.info("[Mem0-SDK] search request: userId={}, agentId={}, query={}", userId, agentId, userQuery.getTextContent());
        return memory.retrieve(userQuery)
                .doOnNext(text -> log.info("[Mem0-SDK] search result: userId={}, length={}",
                        userId, text != null ? text.length() : 0))
                .defaultIfEmpty("");
    }

    @Override
    public Mono<Void> record(String userId, List<Msg> userMsgs, String assistantText, String agentId) {
        // SDK 的 record 只接受 Msg 列表，无法传独立 assistant 文本。
        // 把 assistant 回复也包装成 Msg 一起发，role=ASSISTANT
        Mem0LongTermMemory memory = buildMemory(userId, agentId);

        List<Msg> allMsgs = new ArrayList<>(userMsgs);
        if (assistantText != null && !assistantText.isBlank()) {
            allMsgs.add(Msg.builder()
                    .role(MsgRole.ASSISTANT)
                    .name("assistant")
                    .content(io.agentscope.core.message.TextBlock.builder()
                            .text(assistantText)
                            .build())
                    .build());
        }

        log.info("[Mem0-SDK] record request: userId={}, agentId={}, msgCount={}, hasAssistant={}",
                userId, agentId, allMsgs.size(), assistantText != null && !assistantText.isBlank());

        return memory.record(allMsgs)
                .doOnSuccess(v -> log.info("[Mem0-SDK] record completed: userId={}", userId))
                .doOnError(e -> log.warn("[Mem0-SDK] record failed: userId={}, error={}",
                        userId, e.getMessage()))
                .then();
    }

    @Override
    public String name() {
        return "mem0-sdk";
    }

    /**
     * 构建 Mem0LongTermMemory 实例（每次请求按 userId 动态创建，实现多租户隔离）。
     *
     * <p><b>注意：</b>SDK 版暂不支持 agentId 隔离，如需按 agent 隔离请使用 HTTP 版（Mem0MemoryProvider）。
     */
    private Mem0LongTermMemory buildMemory(String userId, String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            log.warn("[Mem0-SDK] agentId 隔离当前 SDK 不支持，已忽略: agentId={}", agentId);
        }
        return Mem0LongTermMemory.builder()
                .apiBaseUrl(apiBaseUrl)
                .apiKey(apiKey)
                .apiType(apiType)
                .userId(userId)
                .agentName(agentId)
                .build();
    }
}
