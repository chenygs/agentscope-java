package com.brainmed.ai.qa.service;

import com.brainmed.ai.qa.orm.entity.AiConversation;
import com.brainmed.ai.qa.orm.entity.AiConversationShared;
import com.brainmed.ai.qa.orm.entity.AiConversationMessage;
import com.brainmed.ai.qa.orm.repository.AiConversationRepository;
import com.brainmed.ai.qa.orm.repository.AiConversationSharedRepository;
import com.brainmed.ai.qa.orm.repository.AiConversationMessageRepository;
import com.brainmed.ai.qa.orm.vo.ChatMessageVo;
import com.brainmed.ai.qa.orm.vo.SessionSummaryVo;
import com.brainmed.ai.qa.orm.vo.ShareInfoVo;
import com.brainmed.ai.qa.web.resp.page.Pager;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 对话消息落库服务：把 agentscope {@link Msg} 持久化到数据库做完整存档。
 *
 * <p><b>关键约束（WebFlux + JPA）：</b>本项目是响应式（WebFlux），而 JPA 是阻塞 IO。
 * 因此落库一律 fire-and-forget 调度到 {@link Schedulers#boundedElastic()}，
 * 绝不在 Netty event loop 上直接执行阻塞写；落库失败只记日志、不阻断对话。
 *
 * <p>{@code content} 存提取的纯文本（便于查询/列表预览），{@code contentJson} 用
 * agentscope {@link JsonUtils} 序列化整个 {@link Msg}（与 Redis 中的格式一致，可完整还原）。
 */
@Service
public class ConversationMessageService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessageService.class);

    private final AiConversationMessageRepository repository;
    private final AiConversationSharedRepository sharedRepo;
    private final AiConversationRepository conversationRepo;

    public ConversationMessageService(AiConversationMessageRepository repository,
                                      AiConversationSharedRepository sharedRepo,
                                      AiConversationRepository conversationRepo) {
        this.repository = repository;
        this.sharedRepo = sharedRepo;
        this.conversationRepo = conversationRepo;
    }

    /**
     * 异步落库一条消息（fire-and-forget）。
     *
     * @param userId     用户 id
     * @param sessionId  会话 id（前端 threadId）
     * @param runId      本轮运行 id
     * @param msg        agentscope 消息
     * @param seq        轮内顺序
     * @param occurredAt 事件发生时刻（在事件点同步生成，保证回放顺序，不用落库线程时刻）
     */
    public void persistAsync(
            String userId, String sessionId, String runId, Msg msg,
            int seq, LocalDateTime occurredAt) {
        persistAsync(userId, sessionId, runId, msg, seq, occurredAt, null);
    }

    /**
     * 异步落库一条消息（fire-and-forget），带引用来源 JSON。
     *
     * <p>树形结构：非 user 消息会自动解析同 run 的 user 消息作为 parent，
     * 计算 path 和 depth。user 消息自身 parent_id = NULL（根节点）。
     *
     * @param citations  引用来源 JSON（仅最终 assistant 文本回答行有值，其余传 null）
     */
    public void persistAsync(
            String userId, String sessionId, String runId, Msg msg,
            int seq, LocalDateTime occurredAt, String citations) {
        if (msg == null) {
            return;
        }
        Mono.fromRunnable(() -> persist(userId, sessionId, runId, msg, seq, occurredAt, citations))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        e -> log.warn(
                                "对话消息落库失败 user={} session={} seq={}: {}",
                                userId, sessionId, seq, e.toString()));
    }

    /**
     * 异步把续写文本追加到某会话最后一条 assistant 消息记录（content + contentJson 同步更新）。
     * fire-and-forget，落库失败只记日志、不阻断对话。
     */
    
    private void persist(
            String userId, String sessionId, String runId, Msg msg,
            int seq, LocalDateTime occurredAt, String citations) {
        AiConversationMessage entity = new AiConversationMessage();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRunId(runId);
        entity.setMsgId(msg.getId());
        entity.setRole(lc(msg.getRole()));
        entity.setMessageType(resolveMessageType(msg));
        entity.setContent(extractText(msg));
        entity.setContentJson(toJsonSafe(msg));
        entity.setCitations(citations);
        fillToolInfo(entity, msg);
        entity.setSeq(seq);
        entity.setCreatedAt(occurredAt);

        // 树形结构：解析 parent_id，计算 path 和 depth
        boolean isUserMsg = msg.getRole() == MsgRole.USER;
        if (!isUserMsg) {
            Long userMsgId = resolveUserMsgId(userId, sessionId, runId);
            entity.setParentId(userMsgId);
            entity.setDepth(userMsgId != null ? 1 : 0);
        } else {
            entity.setDepth(0);
        }

        repository.save(entity);

        // 保存后回填 path（需要自增 id）
        if (entity.getId() != null) {
            entity.setPath(buildPath(entity.getParentId(), entity.getId()));
            repository.save(entity);
        }

        // 同步会话主表：不存在则创建，已存在则更新活跃时间 + current_message_id
        upsertConversation(userId, sessionId, msg.getRole(), entity.getContent(),
                occurredAt, entity.getId());
    }

    /**
     * 解析同 run 的 user 消息 DB id，用作 parent_id。
     *
     * <p>user 消息在本轮 onAgent 开头 fire-and-forget 落库，
     * 到 onActing / doFinally 时模型推理已耗时数百毫秒，user 消息必定已落库。
     */
    private Long resolveUserMsgId(String userId, String sessionId, String runId) {
        if (runId == null) {
            return null;
        }
        try {
            AiConversationMessage userMsg = repository
                    .findFirstByUserIdAndSessionIdAndRunIdAndRoleOrderByCreatedAtAscIdAsc(
                            userId, sessionId, runId, "user");
            return userMsg != null ? userMsg.getId() : null;
        } catch (Exception e) {
            log.warn("解析 user 消息 id 失败 runId={}: {}", runId, e.toString());
            return null;
        }
    }

    /**
     * 构建物化路径。
     * <p>根节点：{@code /10/}，子节点：{@code /10/11/}
     */
    private static String buildPath(Long parentId, Long selfId) {
        return parentId != null
                ? "/" + parentId + "/" + selfId + "/"
                : "/" + selfId + "/";
    }

    /** 维护 ai_conversation 主表：首次出现创建记录，已存在则更新活跃时间 + current_message_id。 */
    private void upsertConversation(String userId, String sessionId, MsgRole role,
                                     String content, LocalDateTime occurredAt,
                                     Long latestMsgId) {
        AiConversation conv = conversationRepo.findByUserIdAndSessionId(userId, sessionId);
        if (conv == null) {
            conv = new AiConversation();
            conv.setUserId(userId);
            conv.setSessionId(sessionId);
            conv.setCreatedAt(occurredAt);
            // 用第一条 user 消息做标题
            if (role == MsgRole.USER && content != null && !content.isBlank()) {
                String t = content.trim();
                conv.setTitle(t.length() > 24 ? t.substring(0, 24) : t);
            }
        }
        conv.setUpdatedAt(occurredAt);
        if (latestMsgId != null) {
            conv.setCurrentMessageId(latestMsgId);
        }
        conversationRepo.save(conv);
    }

    /** 拉取某用户的会话 id 列表。 */
    public List<String> sessionIds(String userId) {
        return repository.findDistinctSessionIdsByUserId(userId);
    }

    /** 删除某会话的所有消息。 */
    @Transactional
    public void deleteSession(String userId, String sessionId) {
        repository.deleteByUserIdAndSessionId(userId, sessionId);
    }

    /**
     * 分享会话：生成 token 并存库；已分享过则返回已有 token。
     */
    @Transactional
    public ShareInfoVo shareSession(String userId, String sessionId) {
        AiConversationShared existing = sharedRepo.findByUserIdAndSessionId(userId, sessionId);
        if (existing != null) {
            return new ShareInfoVo(existing.getToken(), "/#/share/" + existing.getToken());
        }
        AiConversationShared sc = new AiConversationShared();
        sc.setToken(UUID.randomUUID().toString().replace("-", ""));
        sc.setUserId(userId);
        sc.setSessionId(sessionId);
        sc.setCreatedAt(LocalDateTime.now());
        sharedRepo.save(sc);
        return new ShareInfoVo(sc.getToken(), "/#/share/" + sc.getToken());
    }

    /**
     * 根据 token 获取分享对话的消息列表（公开只读，无需 userId）。
     * 返回 null 表示 token 无效。
     */
    public List<ChatMessageVo> getSharedMessages(String token) {
        AiConversationShared sc = sharedRepo.findByToken(token);
        if (sc == null) {
            return null;
        }
        // 复用已有的 historyAsAguiMessages 方法（用分享者的 userId 查）
        return historyAsAguiMessages(sc.getUserId(), sc.getSessionId());
    }

    /**
     * 拉取某用户的会话摘要列表（按置顶优先 + 最后活跃时间倒序）。
     *
     * <p>标题取首条 user 消息前 24 字，无 user 消息时回退为 "新对话"。
     */
    public List<SessionSummaryVo> sessionSummaries(String userId) {
        List<Object[]> rows = repository.findSessionIdsWithLatestTime(userId);
        // 查询会话主表的置顶 + 标题
        Map<String, AiConversation> convMap = new HashMap<>();
        for (AiConversation c : conversationRepo.findByUserId(userId)) {
            convMap.put(c.getSessionId(), c);
        }

        List<SessionSummaryVo> result = new ArrayList<>();
        for (Object[] row : rows) {
            String sessionId = (String) row[0];
            long updatedAt = ((java.time.LocalDateTime) row[1])
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            AiConversation conv = convMap.get(sessionId);
            boolean pinned = conv != null && conv.isPinned();
            String title = "新对话";
            // 优先用主表存的标题
            if (conv != null && conv.getTitle() != null && !conv.getTitle().isBlank()) {
                title = conv.getTitle();
            } else {
                AiConversationMessage firstUser = repository
                        .findFirstByUserIdAndSessionIdAndRoleOrderByCreatedAtAscIdAsc(userId, sessionId, "user");
                if (firstUser != null && firstUser.getContent() != null && !firstUser.getContent().isBlank()) {
                    String text = firstUser.getContent().trim();
                    title = text.length() > 24 ? text.substring(0, 24) : text;
                }
            }
            result.add(new SessionSummaryVo(sessionId, title, updatedAt, pinned));
        }
        // 置顶优先，再按时间倒序
        result.sort((a, b) -> {
            if (a.pinned() && !b.pinned()) return -1;
            if (!a.pinned() && b.pinned()) return 1;
            return Long.compare(b.updatedAt(), a.updatedAt());
        });
        return result;
    }

    /**
     * 切换会话置顶状态，返回新的置顶状态。
     */
    @Transactional
    public boolean togglePin(String userId, String sessionId) {
        AiConversation conv = conversationRepo.findByUserIdAndSessionId(userId, sessionId);
        if (conv == null) {
            conv = new AiConversation();
            conv.setUserId(userId);
            conv.setSessionId(sessionId);
            conv.setPinned(true);
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            conversationRepo.save(conv);
            return true;
        }
        conv.setPinned(!conv.isPinned());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepo.save(conv);
        return conv.isPinned();
    }

    /**
     * 分页拉取某会话历史并转成 AG-UI 消息序列。
     *
     * <p>分页基于数据库行，每行可能拆出多条 AG-UI 消息（thinking → reasoning、text → assistant）。
     * Pager 的 total/size/current 反映数据库行数，list 为转换后的消息列表。
     */
    public Pager<ChatMessageVo> historyPage(String userId, String sessionId, Pageable pageable) {
        Page<AiConversationMessage> dbPage = repository.findByUserIdAndSessionId(userId, sessionId, pageable);

        List<ChatMessageVo> msgs = new ArrayList<>();
        for (AiConversationMessage row : dbPage.getContent()) {
            Msg msg = deserialize(row.getContentJson());
            if (msg != null) {
                msgs.addAll(toAguiMessages(msg, row.getCitations()));
            } else {
                msgs.add(new ChatMessageVo(
                        row.getMsgId(), row.getRole(), row.getContent(), null, row.getToolCallId(),
                        row.getToolName(), row.getCitations()));
            }
        }

        Pager<ChatMessageVo> pager = new Pager<>(null);
        pager.setList(msgs);
        pager.setTotal(dbPage.getTotalElements());
        pager.setTotalPage(dbPage.getTotalPages());
        pager.setCurrent(dbPage.getNumber() + 1);
        pager.setSize(dbPage.getSize());
        return pager;
    }

    /**
     * 拉取某会话历史并转成 AG-UI 消息序列（供前端 setMessages 注入回放完整轨迹）。
     *
     * <p>每行的 content_json 反序列化回 {@link Msg} 后拆分：思考 → reasoning（id 加 -r）、
     * 文本+工具调用 → assistant、工具结果 → tool。content_json 缺失/损坏时退回行字段构造简单消息。
     */
    public List<ChatMessageVo> historyAsAguiMessages(String userId, String sessionId) {
        List<AiConversationMessage> rows =
                repository.findByUserIdAndSessionIdOrderByCreatedAtAscSeqAscIdAsc(userId, sessionId);

        List<ChatMessageVo> out = new ArrayList<>();
        for (AiConversationMessage row : rows) {
            Msg msg = deserialize(row.getContentJson());
            if (msg != null) {
                out.addAll(toAguiMessages(msg, row.getCitations()));
            } else {
                out.add(
                        new ChatMessageVo(
                                row.getMsgId(),
                                row.getRole(),
                                row.getContent(),
                                null,
                                row.getToolCallId(),
                                row.getToolName(),
                                row.getCitations()));
            }
        }
        return out;
    }

    private Msg deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.getJsonCodec().fromJson(json, Msg.class);
        } catch (Exception e) {
            log.warn("content_json 反序列化失败: {}", e.toString());
            return null;
        }
    }

    /** 把一条 agentscope Msg 拆成若干 AG-UI 消息。 */
    private static List<ChatMessageVo> toAguiMessages(Msg msg, String citations) {
        List<ChatMessageVo> result = new ArrayList<>();
        if (msg.getContent() == null) {
            return result;
        }
        String id = msg.getId();
        MsgRole role = msg.getRole();

        if (role == MsgRole.TOOL) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolResultBlock trb) {
                    String text = extractToolResultText(trb);
                    result.add(
                            new ChatMessageVo(
                                    trb.getId() != null ? trb.getId() : id,
                                    lc(MsgRole.TOOL),
                                    text != null ? text : "",
                                    null,
                                    trb.getId(),
                                    trb.getName(),
                                    null));
                }
            }
            return result;
        }

        StringBuilder text = new StringBuilder();
        List<ChatMessageVo.ToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock think) {
                if (think.getThinking() != null && !think.getThinking().isEmpty()) {
                    // reasoning 单独成一条，id 加 -r 后缀避免与 assistant 同 id 被前端去重
                    result.add(
                            new ChatMessageVo(
                                    id + "-r", ChatMessageVo.ROLE_REASONING, think.getThinking(), null, null, null, null));
                }
            } else if (block instanceof TextBlock tb) {
                if (tb.getText() != null) {
                    if (!text.isEmpty()) {
                        text.append("\n");
                    }
                    text.append(tb.getText());
                }
            } else if (block instanceof ToolUseBlock tub) {
                toolCalls.add(
                        new ChatMessageVo.ToolCall(
                                tub.getId(),
                                "function",
                                new ChatMessageVo.FunctionCall(
                                        tub.getName(), serializeArgs(tub))));
            }
        }

        String roleStr =
                role == MsgRole.USER ? lc(MsgRole.USER)
                        : (role == MsgRole.SYSTEM ? lc(MsgRole.SYSTEM) : lc(MsgRole.ASSISTANT));
        String content = text.isEmpty() ? null : text.toString();
        // 主消息：assistant 即便无文本但有工具调用也要发；user 总是要发
        if (content != null || !toolCalls.isEmpty() || role == MsgRole.USER) {
            result.add(
                    new ChatMessageVo(
                            id, roleStr, content, toolCalls.isEmpty() ? null : toolCalls, null,
                            null, MsgRole.ASSISTANT == role ? citations : null));
        }
        return result;
    }

    private static String serializeArgs(ToolUseBlock tub) {
        if (tub.getInput() == null) {
            return "{}";
        }
        try {
            return JsonUtils.getJsonCodec().toJson(tub.getInput());
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String extractToolResultText(ToolResultBlock trb) {
        if (trb.getOutput() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock output : trb.getOutput()) {
            if (output instanceof TextBlock tb && tb.getText() != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(tb.getText());
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        // AgentScope 可能把 Mono<String> 返回值 JSON 序列化，导致文本被多余的 "" 包裹
        String text = sb.toString().trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }


    // ----------------------------------------------------------------------
    // 转换辅助
    // ----------------------------------------------------------------------

    /** 判定消息主类型。一条 Msg 可能混合多种 block，取最显著的一个；细节以 contentJson 为准。 */
    private static String resolveMessageType(Msg msg) {
        boolean hasToolResult = false;
        boolean hasToolUse = false;
        boolean hasText = false;
        boolean hasThinking = false;
        if (msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolResultBlock) {
                    hasToolResult = true;
                } else if (block instanceof ToolUseBlock) {
                    hasToolUse = true;
                } else if (block instanceof TextBlock) {
                    hasText = true;
                } else if (block instanceof ThinkingBlock) {
                    hasThinking = true;
                }
            }
        }
        if (hasToolResult) {
            return "tool_result";
        }
        if (hasToolUse) {
            return "tool_use";
        }
        if (hasText) {
            return "text";
        }
        if (hasThinking) {
            return "thinking";
        }
        return "text";
    }

    /** 拼接所有 TextBlock 文本，并提取 ToolResultBlock 的结果文本。 */
    private static String extractText(Msg msg) {
        if (msg.getContent() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(textBlock.getText());
            } else if (block instanceof ToolResultBlock trb) {
                String toolText = extractToolResultText(trb);
                if (toolText != null) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(toolText);
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }


    /** 取第一个工具相关 block 的 name/id 填入实体。 */
    private static void fillToolInfo(AiConversationMessage entity, Msg msg) {
        if (msg.getContent() == null) {
            return;
        }
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolUseBlock toolUse) {
                entity.setToolName(toolUse.getName());
                entity.setToolCallId(toolUse.getId());
                return;
            }
            if (block instanceof ToolResultBlock toolResult) {
                entity.setToolName(toolResult.getName());
                entity.setToolCallId(toolResult.getId());
                return;
            }
        }
    }

    private static String toJsonSafe(Msg msg) {
        try {
            return JsonUtils.getJsonCodec().toJson(msg);
        } catch (Exception e) {
            log.warn("Msg 序列化失败 id={}: {}", msg.getId(), e.toString());
            return null;
        }
    }

    /** MsgRole 转小写字符串，与 DB / AG-UI 协议对齐。 */
    private static String lc(MsgRole role) {
        return role != null ? role.name().toLowerCase() : null;
    }
}
