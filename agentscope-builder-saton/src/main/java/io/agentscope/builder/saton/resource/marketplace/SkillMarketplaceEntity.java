package io.agentscope.builder.saton.resource.marketplace;

import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "skill_marketplace",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_marketplace_owner_mkid",
                columnNames = {"owner_id", "marketplace_id"}),
        indexes = @Index(name = "ix_skill_marketplace_owner", columnList = "owner_id")
)
@Getter
@Setter
@NoArgsConstructor
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
}
