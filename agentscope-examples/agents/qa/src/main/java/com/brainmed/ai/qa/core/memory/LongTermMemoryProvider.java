package com.brainmed.ai.qa.core.memory;

import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 长期记忆提供者接口：抽象检索和记录能力，支持多种后端（Mem0 / Viking 记忆库等）。
 *
 * <p>实现类应自行处理多租户隔离（按 userId）。
 */
public interface LongTermMemoryProvider {

    /**
     * 检索与用户当前查询相关的长期记忆文本。
     *
     * @param userQuery 用户最后一条消息
     * @param userId    用户标识（多租户隔离）
     * @param agentId   Agent 标识（按 agent 隔离记忆，可为 null）
     * @return 记忆文本（可能为空字符串，表示无相关记忆）
     */
    Mono<String> search(Msg userQuery, String userId, String agentId);

    /**
     * 异步记录一轮完整对话（user + assistant），供后端抽取长期记忆。
     *
     * <p>必须同时传入用户消息和助手回复，记忆后端才能判断对话是否有实质内容。
     * 只发 user 消息会导致后端把闲聊/打招呼也当记忆存储。
     *
     * @param userId        用户标识
     * @param userMsgs      本轮所有 user 角色的消息
     * @param assistantText 助手最终回复文本（可为空，空时不记录）
     * @param agentId       Agent 标识（按 agent 隔离记忆，可为 null）
     * @return 记录完成的信号
     */
    Mono<Void> record(String userId, List<Msg> userMsgs, String assistantText, String agentId);

    /**
     * 当前提供者名称（用于日志标识），如 "mem0"、"viking"。
     */
    String name();
}
