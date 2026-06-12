package io.agentscope.builder.saton.resource.marketplace;

import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import jakarta.persistence.*;

@Entity
@Table(
        name = "skill_marketplace",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_marketplace_owner_mkid",
                columnNames = {"owner_id", "marketplace_id"}),
        indexes = @Index(name = "ix_skill_marketplace_owner", columnList = "owner_id")
)
public class SkillMarketplaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    @Column(name = "marketplace_id", length = 128, nullable = false)
    private String marketplaceId;

    /** Marketplace type: "git" / "nacos". */
    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Lob
    @Convert(converter = EncryptedJsonConverter.class)
    @Column(name = "props_json")
    private String propsJson;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getMarketplaceId() { return marketplaceId; }
    public void setMarketplaceId(String marketplaceId) { this.marketplaceId = marketplaceId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPropsJson() { return propsJson; }
    public void setPropsJson(String propsJson) { this.propsJson = propsJson; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
