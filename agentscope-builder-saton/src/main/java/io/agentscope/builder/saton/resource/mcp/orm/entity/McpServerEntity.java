package io.agentscope.builder.saton.resource.mcp.orm.entity;

import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "mcp_server",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mcp_server_owner_name",
                columnNames = {"owner_id", "name"}),
        indexes = @Index(name = "ix_mcp_server_owner", columnList = "owner_id")
)
@Getter
@Setter
@NoArgsConstructor
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** Transport type: "stdio" / "sse" / "http". */
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
