package io.agentscope.builder.saton.resource.repository;

import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import jakarta.persistence.*;

@Entity
@Table(
        name = "skill_repository",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_repository_owner_name",
                columnNames = {"owner_id", "name"}),
        indexes = @Index(name = "ix_skill_repository_owner", columnList = "owner_id")
)
public class SkillRepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** Repository type: "filesystem" / "git". */
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPropsJson() { return propsJson; }
    public void setPropsJson(String propsJson) { this.propsJson = propsJson; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
