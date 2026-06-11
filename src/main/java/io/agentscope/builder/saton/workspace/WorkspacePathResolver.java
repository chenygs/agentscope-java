package io.agentscope.builder.saton.workspace;

import java.nio.file.Path;

/**
 * (ownerId, agentDefId, relPath) → 绝对 Path 解析 + 越界校验。
 *
 * <p>每个 agent 一个独立目录 {@code <root>/<ownerId>/<agentDefId>/files/}。所有用户输入
 * 的相对路径必须落在该目录内 —— 任何 {@code ..} 穿越、绝对路径、空白输入都拒绝。
 */
public class WorkspacePathResolver {

    private final Path root;

    public WorkspacePathResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 该 agent 的文件根目录（{@code .../<ownerId>/<agentId>/files}）。 */
    public Path agentRoot(String ownerId, Long agentDefId) {
        return root.resolve(ownerId).resolve(String.valueOf(agentDefId)).resolve("files");
    }

    /** 解析相对路径到绝对路径，校验未越界。 */
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
