package io.agentscope.builder.saton.agent.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_share",
       indexes = @Index(name = "ix_agent_share_grantee", columnList = "grantee_id"))
@Getter
@Setter
@NoArgsConstructor
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
}
