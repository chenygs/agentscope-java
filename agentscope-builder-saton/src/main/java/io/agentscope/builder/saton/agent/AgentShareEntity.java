package io.agentscope.builder.saton.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_share",
       indexes = @Index(name = "ix_agent_share_grantee", columnList = "grantee_id"))
public class AgentShareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_def_id", nullable = false)
    private Long agentDefId;

    @Column(name = "grantee_type", length = 16, nullable = false)
    private String granteeType = "USER";

    @Column(name = "grantee_id", length = 128, nullable = false)
    private String granteeId;

    @Column(name = "tier", length = 16, nullable = false)
    private String tier;

    @Column(name = "created_by", length = 128, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentDefId() { return agentDefId; }
    public void setAgentDefId(Long agentDefId) { this.agentDefId = agentDefId; }
    public String getGranteeType() { return granteeType; }
    public void setGranteeType(String granteeType) { this.granteeType = granteeType; }
    public String getGranteeId() { return granteeId; }
    public void setGranteeId(String granteeId) { this.granteeId = granteeId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
