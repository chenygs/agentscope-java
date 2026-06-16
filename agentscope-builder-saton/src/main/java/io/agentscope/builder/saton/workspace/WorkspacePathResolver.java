package io.agentscope.builder.saton.workspace;

import java.nio.file.Path;

/**
 * (ownerId, agentId, relPath) → 绝对 Path 解析 + 越界校验。
 *
 * <p>新版目录结构跟 AgentScope 2.0 Harness workspace 设计对齐:
 * <pre>
 * &lt;root&gt;/&lt;ownerId&gt;/                  ← user 级 workspace,跨 agent 共享
 *   ├── AGENTS.md                       人格 / 行为约定 (用户编辑)
 *   ├── MEMORY.md                       长期记忆 (harness 写入,LLM 周期性合并)
 *   ├── memory/YYYY-MM-DD.md            日流水账 (harness 写入)
 *   ├── skills/&lt;name&gt;/SKILL.md          可复用技能 (跨 agent 共享)
 *   └── agents/&lt;agentId&gt;/               agent 私有数据 (注意:agentId 是业务 id 字符串,
 *         ├── sessions/...                                 不是数据库主键 — harness 用
 *         └── tasks/...                                    {@code def.getAgentId()} 拼路径)
 * </pre>
 *
 * <p>之前版本是 {@code <root>/<ownerId>/<agentDefId>/files/<userId>/agents/<id>/...} 这套
 * 双重路径前缀,导致 MEMORY.md 落在每个 agent 自己根下,无法跨 agent 共享 — 跟官方设计不符。
 *
 * <p>注意:Harness 内部的 {@code NamespaceFactory} 会读 {@code RuntimeContext.userId} 并自动
 * 把 userId 拼成路径段 {@code <workspace>/<userId>/...} (见 {@code HarnessAgent.build()})。
 * 因此 {@link io.agentscope.builder.saton.agent.runtime.AgentBuildOrchestrator} 给 harness
 * 传 {@link #root()}(裸 root)而非 {@link #userRoot(String)},让 harness 自己拼 userId 段;
 * 否则会得到 {@code <root>/<userId>/<userId>/...} 双重嵌套。skillFactory / middlewareFactory
 * 在 build 期一次性调用,拿不到 RuntimeContext,所以仍然要传 {@link #userRoot(String)} —
 * 由 saton 这边把 userId 拼好。
 */
public class WorkspacePathResolver {

    private final Path root;

    public WorkspacePathResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 全局 workspace 根目录 ({@code <root>}) — 主要给 harness 用,它会自己拼 userId 段。 */
    public Path root() {
        return root;
    }

    /** 用户的 workspace 根 ({@code <root>/<ownerId>}) — 跨 agent 共享数据放这。 */
    public Path userRoot(String ownerId) {
        return root.resolve(ownerId);
    }

    /**
     * Agent 私有数据根 ({@code <root>/<ownerId>/agents/<agentId>}) — 仅该 agent 看到。
     *
     * <p>{@code agentId} 必须是业务 id 字符串(对应 {@code AgentDefinitionEntity.agentId}
     * 字段、{@code HarnessAgent.builder().name(...)} 用的值),不是数据库主键。Harness
     * 在传入 workspace 下会按 {@code agents/<name>/sessions/...} 物理落盘,saton 这边
     * 必须用同一个标识符才能找到那批文件。
     */
    public Path agentRoot(String ownerId, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        return userRoot(ownerId).resolve("agents").resolve(agentId);
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

    /** 解析 agent 私有相对路径 → 绝对 Path,校验未越出 {@link #agentRoot(String, String)}。 */
    public Path resolve(String ownerId, String agentId, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        Path agentRoot = agentRoot(ownerId, agentId);
        Path target = agentRoot.resolve(relPath).normalize();
        if (!target.startsWith(agentRoot)) {
            throw new IllegalArgumentException("path escapes workspace: " + relPath);
        }
        return target;
    }
}
