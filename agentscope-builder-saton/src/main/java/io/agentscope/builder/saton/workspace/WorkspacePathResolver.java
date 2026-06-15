package io.agentscope.builder.saton.workspace;

import java.nio.file.Path;

/**
 * (ownerId, agentDefId, relPath) → 绝对 Path 解析 + 越界校验。
 *
 * <p>新版目录结构跟 AgentScope 2.0 Harness workspace 设计对齐:
 * <pre>
 * &lt;root&gt;/&lt;ownerId&gt;/                  ← user 级 workspace,跨 agent 共享
 *   ├── AGENTS.md                       人格 / 行为约定 (用户编辑)
 *   ├── MEMORY.md                       长期记忆 (harness 写入,LLM 周期性合并)
 *   ├── memory/YYYY-MM-DD.md            日流水账 (harness 写入)
 *   ├── skills/&lt;name&gt;/SKILL.md          可复用技能 (跨 agent 共享)
 *   └── agents/&lt;agentDefId&gt;/            agent 私有数据
 *         ├── sessions/...              对话日志、状态
 *         └── tasks/...                 子 agent 后台任务
 * </pre>
 *
 * <p>之前版本是 {@code <root>/<ownerId>/<agentDefId>/files/<userId>/agents/<id>/...} 这套
 * 双重路径前缀,导致 MEMORY.md 落在每个 agent 自己根下,无法跨 agent 共享 — 跟官方设计不符。
 *
 * <p>注意:Harness 自己会在它拿到的 workspace 路径下创建 {@code agents/<id>/sessions/} 这层。
 * {@link io.agentscope.builder.saton.agent.runtime.AgentBuildOrchestrator} 应该传
 * {@link #userRoot(String)} 而不是 {@link #agentRoot(String, Long)} 给 harness。
 */
public class WorkspacePathResolver {

    private final Path root;

    public WorkspacePathResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 用户的 workspace 根 ({@code <root>/<ownerId>}) — 跨 agent 共享数据放这。 */
    public Path userRoot(String ownerId) {
        return root.resolve(ownerId);
    }

    /** Agent 私有数据根 ({@code <root>/<ownerId>/agents/<agentDefId>}) — 仅该 agent 看到。 */
    public Path agentRoot(String ownerId, Long agentDefId) {
        return userRoot(ownerId).resolve("agents").resolve(String.valueOf(agentDefId));
    }

    /** 解析 user 级相对路径 → 绝对 Path,校验未越出 {@link #userRoot(String)}。 */
    public Path resolveUser(String ownerId, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        Path userRoot = userRoot(ownerId);
        Path target = userRoot.resolve(relPath).normalize();
        if (!target.startsWith(userRoot)) {
            throw new IllegalArgumentException("path escapes user workspace: " + relPath);
        }
        return target;
    }

    /** 解析 agent 私有相对路径 → 绝对 Path,校验未越出 {@link #agentRoot(String, Long)}。 */
    public Path resolve(String ownerId, Long agentDefId, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        Path agentRoot = agentRoot(ownerId, agentDefId);
        Path target = agentRoot.resolve(relPath).normalize();
        if (!target.startsWith(agentRoot)) {
            throw new IllegalArgumentException("path escapes workspace: " + relPath);
        }
        return target;
    }
}
