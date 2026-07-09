package com.brainmed.ai.qa.service;

import com.brainmed.ai.qa.orm.entity.AiAgentSessionStateEntity;
import com.brainmed.ai.qa.orm.repository.AiAgentSessionStateRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.ListHashUtil;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 运行时状态存储 — JPA 版本，对应官方 {@code MysqlAgentStateStore}。
 *
 * <p>数据落 {@code ppt_session} 表（实体 {@link AiAgentSessionStateEntity}），存储策略与
 * {@code io.agentscope.extensions.mysql.state.MysqlAgentStateStore} 保持一致：
 *
 * <ul>
 *   <li>{@code (userId, sessionId)} 打包进 {@code session_id} 列，格式
 *       {@code "{userSegment}:{sessionId}"}，匿名时 {@code userSegment = "__anon__"}。</li>
 *   <li>单值状态：{@code item_index = 0}，state_key 即业务键。</li>
 *   <li>列表状态：每项一行，{@code item_index = 0,1,2,...}；另存一行
 *       {@code state_key = "xxx:_hash"} 用作变更检测（仅追加 / 整体重写）。</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JpaSessionStateStore implements AgentStateStore {

    /** 匿名 user 的占位段 */
    private static final String ANON_USER_SEGMENT = "__anon__";

    /** 列表哈希键后缀 */
    private static final String HASH_KEY_SUFFIX = ":_hash";

    /** 单值 / 哈希值固定 item_index */
    private static final int SINGLE_STATE_INDEX = 0;

    private final AiAgentSessionStateRepository repository;

    // ============== AgentStateStore 接口实现 ==============

    @Override
    @Transactional
    public void save(String userId, String sessionId, String key, State value) {
        log.info("[state-thread] op=save key={} thread={}", key, Thread.currentThread().getName());
        validateSessionId(sessionId);
        validateStateKey(key);

        String sid = packSessionId(userId, sessionId);
        String json = JsonUtils.getJsonCodec().toJson(value);

        upsertRow(sid, key, SINGLE_STATE_INDEX, json);
    }

    @Override
    @Transactional
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        log.info("[state-thread] op=saveList key={} size={} thread={}",
                key, values == null ? 0 : values.size(), Thread.currentThread().getName());
        validateSessionId(sessionId);
        validateStateKey(key);

        if (values == null || values.isEmpty()) {
            return;
        }

        String sid = packSessionId(userId, sessionId);
        String hashKey = key + HASH_KEY_SUFFIX;
        String currentHash = ListHashUtil.computeHash(values);
        String storedHash = repository
                .findBySessionIdAndStateKeyOrderByItemIndexAsc(sid, hashKey)
                .stream()
                .findFirst()
                .map(AiAgentSessionStateEntity::getStateData)
                .orElse(null);
        int existingCount = (int) repository.countBySessionIdAndStateKey(sid, key);

        boolean needsFullRewrite = ListHashUtil.needsFullRewrite(values, storedHash, existingCount);

        if (needsFullRewrite) {
            repository.deleteBySessionIdAndStateKey(sid, key);
            insertItems(sid, key, values, 0);
            upsertRow(sid, hashKey, SINGLE_STATE_INDEX, currentHash);
        } else if (values.size() > existingCount) {
            List<? extends State> newItems = values.subList(existingCount, values.size());
            insertItems(sid, key, newItems, existingCount);
            upsertRow(sid, hashKey, SINGLE_STATE_INDEX, currentHash);
        }
        // else: 无变化，跳过
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        log.info("[state-thread] op=get key={} thread={}", key, Thread.currentThread().getName());
        validateSessionId(sessionId);
        validateStateKey(key);

        String sid = packSessionId(userId, sessionId);
        return repository
                .findById(new AiAgentSessionStateEntity.AgentSessionStateId(sid, key, SINGLE_STATE_INDEX))
                .map(row -> JsonUtils.getJsonCodec().fromJson(row.getStateData(), type));
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        log.info("[state-thread] op=getList key={} thread={}", key, Thread.currentThread().getName());
        validateSessionId(sessionId);
        validateStateKey(key);

        String sid = packSessionId(userId, sessionId);
        return repository.findBySessionIdAndStateKeyOrderByItemIndexAsc(sid, key)
                .stream()
                .map(row -> JsonUtils.getJsonCodec().fromJson(row.getStateData(), itemType))
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        validateSessionId(sessionId);
        return repository.existsBySessionId(packSessionId(userId, sessionId));
    }

    @Override
    @Transactional
    public void delete(String userId, String sessionId) {
        validateSessionId(sessionId);
        repository.deleteBySessionId(packSessionId(userId, sessionId));
    }

    @Override
    @Transactional
    public void delete(String userId, String sessionId, String key) {
        validateSessionId(sessionId);
        validateStateKey(key);

        String sid = packSessionId(userId, sessionId);
        repository.deleteBySessionIdAndStateKey(sid, key);
        repository.deleteBySessionIdAndStateKey(sid, key + HASH_KEY_SUFFIX);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        String prefix = userSegment(userId) + ":";
        Set<String> result = new HashSet<>();
        for (String sid : repository.findDistinctSessionIds()) {
            if (sid.startsWith(prefix)) {
                result.add(sid.substring(prefix.length()));
            }
        }
        return result;
    }

    @Override
    public void close() {
        // 资源由 Spring 管理
    }

    // ============== 私有辅助方法 ==============

    /**
     * 将 {@code (userId, sessionId)} 打包成 {@code "{userSegment}:{sessionId}"}，
     * 与官方 {@code MysqlAgentStateStore} 保持一致。
     */
    private String packSessionId(String userId, String sessionId) {
        return userSegment(userId) + ":" + sessionId;
    }

    private String userSegment(String userId) {
        return (userId == null || userId.isEmpty()) ? ANON_USER_SEGMENT : userId;
    }

    /** 单行 upsert（INSERT or UPDATE state_data）。JPA 端通过 save() 实现。 */
    private void upsertRow(String sessionId, String stateKey, int itemIndex, String stateData) {
        AiAgentSessionStateEntity.AgentSessionStateId id = new AiAgentSessionStateEntity.AgentSessionStateId(sessionId, stateKey, itemIndex);
        AiAgentSessionStateEntity row = repository.findById(id).orElseGet(() ->
                AiAgentSessionStateEntity.builder()
                        .sessionId(sessionId)
                        .stateKey(stateKey)
                        .itemIndex(itemIndex)
                        .build()
        );
        row.setStateData(stateData);
        repository.save(row);
    }

    /** 批量插入列表项，从 startIndex 开始递增 item_index。 */
    private void insertItems(String sessionId, String key, List<? extends State> items, int startIndex) {
        List<AiAgentSessionStateEntity> batch = new ArrayList<>(items.size());
        int index = startIndex;
        for (State item : items) {
            batch.add(AiAgentSessionStateEntity.builder()
                    .sessionId(sessionId)
                    .stateKey(key)
                    .itemIndex(index++)
                    .stateData(JsonUtils.getJsonCodec().toJson(item))
                    .build());
        }
        repository.saveAll(batch);
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (sessionId.contains("/") || sessionId.contains("\\")) {
            throw new IllegalArgumentException("Session ID cannot contain path separators");
        }
        if (sessionId.length() > 255) {
            throw new IllegalArgumentException("Session ID cannot exceed 255 characters");
        }
    }

    private void validateStateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("State key cannot be null or empty");
        }
        if (key.length() > 255) {
            throw new IllegalArgumentException("State key cannot exceed 255 characters");
        }
    }
}
