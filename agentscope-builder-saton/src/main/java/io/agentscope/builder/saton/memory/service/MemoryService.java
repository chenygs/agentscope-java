package io.agentscope.builder.saton.memory.service;

import io.agentscope.builder.saton.memory.orm.dto.MemoryFileVO;
import io.agentscope.builder.saton.memory.orm.dto.MemorySummaryVO;
import io.agentscope.builder.saton.workspace.service.WorkspacePathResolver;
import io.agentscope.builder.saton.workspace.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户级记忆文件(AGENTS.md / MEMORY.md ...)的读写门面。
 *
 * <p>所有文件落在 {@code <workspace.root>/<ownerId>/} 下,跨 agent 共享 — 这是
 * commit 40caefcd 路径架构改造的目的。{@link MemoryKind} 决定 URL slug → 文件名
 * 的映射。
 *
 * <p>实现上委托 {@link WorkspaceService} 的 {@code readUser} / {@code writeUser},
 * 并通过 {@link WorkspacePathResolver#userRoot(String)} 获取摘要展示用的根路径。
 */
@Slf4j
@Service
public class MemoryService {

    private final WorkspacePathResolver resolver;
    private final WorkspaceService workspace;

    public MemoryService(WorkspacePathResolver resolver, WorkspaceService workspace) {
        this.resolver = resolver;
        this.workspace = workspace;
    }

    /** 读取指定 kind 的内容;文件不存在返回空串(不抛 404 — 前端体验更顺)。 */
    public String read(String ownerId, MemoryKind kind) {
        String content = workspace.readUser(ownerId, kind.fileName());
        return content == null ? "" : content;
    }

    /** 全文覆写;{@code content} 为 null 时写入空串。 */
    public void write(String ownerId, MemoryKind kind, String content) {
        workspace.writeUser(ownerId, kind.fileName(), content);
    }

    /** 返回所有已知 kind 的元信息 + 用户 workspace 根路径,用于前端列表展示。 */
    public MemorySummaryVO summary(String ownerId) {
        Path root = resolver.userRoot(ownerId);
        List<MemoryFileVO> files = new ArrayList<>(MemoryKind.values().length);
        for (MemoryKind kind : MemoryKind.values()) {
            files.add(probe(root, kind));
        }
        return new MemorySummaryVO(root.toString(), files);
    }

    private MemoryFileVO probe(Path userRoot, MemoryKind kind) {
        Path p = userRoot.resolve(kind.fileName());
        if (!Files.isRegularFile(p)) {
            return new MemoryFileVO(kind.slug(), kind.fileName(), false, 0L, null);
        }
        long size = 0L;
        Long modifiedAt = null;
        try {
            size = Files.size(p);
            modifiedAt = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            log.warn("probe memory file failed: {} — {}", p, e.getMessage());
        }
        return new MemoryFileVO(kind.slug(), kind.fileName(), true, size, modifiedAt);
    }
}
