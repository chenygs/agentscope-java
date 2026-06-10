package io.github.chenygs.pptagent.agent.state;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 会话状态实体 — JPA 映射 ppt_session 表
 *
 * <p>表结构兼容 AgentScope MysqlSession，复合主键 (session_id, state_key, item_index)。
 *
 * <ul>
 *   <li>单值状态：item_index = 0
 *   <li>列表状态：item_index = 0, 1, 2, ...
 *   <li>列表哈希：state_key = "xxx:_hash", item_index = 0
 * </ul>
 *
 * <p>session_id 由 SessionKey.toIdentifier() 提供，业务上格式为 "userId:conversationId"。
 */
@Entity
@Table(name = "ppt_session", indexes = {
        @Index(name = "idx_session_state", columnList = "session_id, state_key, item_index")
})
@IdClass(TAgentStateStore.TAgentStateStoreId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TAgentStateStore {

    @Id
    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Id
    @Column(name = "state_key", nullable = false, length = 255)
    private String stateKey;

    @Id
    @Column(name = "item_index", nullable = false)
    private Integer itemIndex;

    @Lob
    @Column(name = "state_data", nullable = false, columnDefinition = "LONGTEXT")
    private String stateData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 复合主键 ID 类（JPA 要求）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TAgentStateStoreId implements Serializable {
        private String sessionId;
        private String stateKey;
        private Integer itemIndex;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TAgentStateStoreId that)) return false;
            return Objects.equals(sessionId, that.sessionId)
                    && Objects.equals(stateKey, that.stateKey)
                    && Objects.equals(itemIndex, that.itemIndex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, stateKey, itemIndex);
        }
    }
}
