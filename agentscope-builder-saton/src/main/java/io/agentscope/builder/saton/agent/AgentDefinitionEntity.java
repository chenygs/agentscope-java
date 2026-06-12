package io.agentscope.builder.saton.agent;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 配置。表 {@code agent_definition}。
 *
 * <p>所有 JSON 列（tool_specs_json / skill_refs_json / hook_specs_json / subagent_refs_json /
 * skill_repositories_json）都 <b>不</b>加密 —— 它们不含 secret，引用的 secret 都在
 * {@code model_provider} / {@code mcp_server} 等独立资源表里。
 *
 * <p>M4 仅使用 {@code sys_prompt + default_model_provider_id + max_iters} 三字段构建 ReActAgent；
 * tool / skill / hook / subagent_refs JSON 列在 M5-M7 才会被消费。
 */
@Entity
@Table(
        name = "agent_definition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_definition_owner_agent",
                columnNames = {"owner_id", "agent_id"}),
        indexes = {
                @Index(name = "ix_agent_definition_owner", columnList = "owner_id"),
                @Index(name = "ix_agent_definition_agent_id", columnList = "agent_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AgentDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    /** 业务唯一标识，per-owner 唯一。 */
    @Column(name = "agent_id", length = 128, nullable = false)
    private String agentId;

    @Column(name = "name", length = 200)
    private String name;

    @Lob
    @Column(name = "description")
    private String description;

    @Lob
    @Column(name = "sys_prompt")
    private String sysPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 50, nullable = false)
    private AgentType agentType;

    @Column(name = "default_model_provider_id", nullable = false)
    private Long defaultModelProviderId;

    @Column(name = "max_iters")
    private Integer maxIters;

    @Column(name = "workspace_path", length = 1024)
    private String workspacePath;

    @Lob @Column(name = "tool_specs_json") private String toolSpecsJson;
    @Lob @Column(name = "skill_refs_json") private String skillRefsJson;
    @Lob @Column(name = "hook_specs_json") private String hookSpecsJson;
    @Lob @Column(name = "subagent_refs_json") private String subagentRefsJson;
    @Lob @Column(name = "skill_repositories_json") private String skillRepositoriesJson;

    @Column(name = "sandbox_mode", length = 16)
    private String sandboxMode;

    @Column(name = "sandbox_scope", length = 16)
    private String sandboxScope;

    @Column(name = "run_as", length = 20)
    private String runAs;

    @Column(name = "fork_of", length = 128)
    private String forkOf;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
