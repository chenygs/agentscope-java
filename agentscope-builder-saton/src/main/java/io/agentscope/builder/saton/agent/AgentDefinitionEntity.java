package io.agentscope.builder.saton.agent;

import jakarta.persistence.*;

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

    /** "react"（M4 默认）/ "harness"（M6+）。 */
    @Column(name = "agent_type", length = 50, nullable = false)
    private String agentType;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSysPrompt() { return sysPrompt; }
    public void setSysPrompt(String sysPrompt) { this.sysPrompt = sysPrompt; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Long getDefaultModelProviderId() { return defaultModelProviderId; }
    public void setDefaultModelProviderId(Long defaultModelProviderId) {
        this.defaultModelProviderId = defaultModelProviderId;
    }
    public Integer getMaxIters() { return maxIters; }
    public void setMaxIters(Integer maxIters) { this.maxIters = maxIters; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public String getToolSpecsJson() { return toolSpecsJson; }
    public void setToolSpecsJson(String toolSpecsJson) { this.toolSpecsJson = toolSpecsJson; }
    public String getSkillRefsJson() { return skillRefsJson; }
    public void setSkillRefsJson(String skillRefsJson) { this.skillRefsJson = skillRefsJson; }
    public String getHookSpecsJson() { return hookSpecsJson; }
    public void setHookSpecsJson(String hookSpecsJson) { this.hookSpecsJson = hookSpecsJson; }
    public String getSubagentRefsJson() { return subagentRefsJson; }
    public void setSubagentRefsJson(String subagentRefsJson) { this.subagentRefsJson = subagentRefsJson; }
    public String getSkillRepositoriesJson() { return skillRepositoriesJson; }
    public void setSkillRepositoriesJson(String skillRepositoriesJson) {
        this.skillRepositoriesJson = skillRepositoriesJson;
    }
    public String getSandboxMode() { return sandboxMode; }
    public void setSandboxMode(String sandboxMode) { this.sandboxMode = sandboxMode; }
    public String getSandboxScope() { return sandboxScope; }
    public void setSandboxScope(String sandboxScope) { this.sandboxScope = sandboxScope; }
    public String getRunAs() { return runAs; }
    public void setRunAs(String runAs) { this.runAs = runAs; }
    public String getForkOf() { return forkOf; }
    public void setForkOf(String forkOf) { this.forkOf = forkOf; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
