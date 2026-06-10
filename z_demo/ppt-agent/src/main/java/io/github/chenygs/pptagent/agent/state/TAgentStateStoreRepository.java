package io.github.chenygs.pptagent.agent.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话状态 Repository — JPA 操作 ppt_session 表
 */
@Repository
public interface TAgentStateStoreRepository
        extends JpaRepository<TAgentStateStore, TAgentStateStore.TAgentStateStoreId> {

    /** 按 sessionId + stateKey 查询所有项（用于列表读取，按 item_index 升序） */
    List<TAgentStateStore> findBySessionIdAndStateKeyOrderByItemIndexAsc(
            String sessionId, String stateKey);

    /** 查询某个状态键的项数（用于列表大小判断） */
    long countBySessionIdAndStateKey(String sessionId, String stateKey);

    /** 判断 session 是否存在（任一记录即可） */
    boolean existsBySessionId(String sessionId);

    /** 删除整个 session */
    @Modifying
    @Transactional
    long deleteBySessionId(String sessionId);

    /** 删除某个 session 下指定 stateKey 的所有项 */
    @Modifying
    @Transactional
    long deleteBySessionIdAndStateKey(String sessionId, String stateKey);

    /** 列出所有不同的 sessionId */
    @Query("SELECT DISTINCT p.sessionId FROM PptSession p ORDER BY p.sessionId")
    List<String> findDistinctSessionIds();
}
