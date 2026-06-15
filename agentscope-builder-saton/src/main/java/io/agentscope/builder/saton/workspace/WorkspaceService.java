package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 每个 agent 的 workspace 文件读写。纯 JDK NIO,单文件单调用,无并发原子性保证
 * (单人视角下够用)。
 *
 * <p>所有路径校验通过 {@link WorkspacePathResolver} 完成；越界请求抛
 * {@link IllegalArgumentException}。
 */
@Slf4j
@Service
public class WorkspaceService {

    /** 单文件最大 512 KB；超过返回截断提示(参照原 builder)。 */
    private static final int MAX_READ = 512 * 1024;

    private final WorkspacePathResolver resolver;

    public WorkspaceService(WorkspacePathResolver resolver) {
        this.resolver = resolver;
    }

    public WorkspaceSummaryVO summary(String ownerId, Long agentDefId) {
        Path root = resolver.agentRoot(ownerId, agentDefId);
        int count = 0;
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                count = (int) walk.filter(Files::isRegularFile).count();
            } catch (IOException e) {
                log.warn("summary walk failed: {}", e.getMessage());
            }
        }
        return new WorkspaceSummaryVO(root.toString(), count);
    }

    public List<FileNodeVO> list(String ownerId, Long agentDefId) {
        return listAt(ownerId, agentDefId, null);
    }

    /**
     * 列出某个相对子目录下的第一层文件/目录。{@code subPath} 为 null 或空时等价于根目录。
     * 越界 / 不存在 / 不是目录 → 抛 {@link IllegalArgumentException} 或返回空列表。
     */
    public List<FileNodeVO> listAt(String ownerId, Long agentDefId, String subPath) {
        Path root = resolver.agentRoot(ownerId, agentDefId);
        Path target = (subPath == null || subPath.isBlank())
                ? root
                : resolver.resolve(ownerId, agentDefId, subPath);
        if (!Files.isDirectory(target)) return List.of();
        List<FileNodeVO> out = new ArrayList<>();
        try (Stream<Path> children = Files.list(target)) {
            children.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> out.add(toNode(p, root)));
        } catch (IOException e) {
            log.warn("list failed: {}", e.getMessage());
        }
        return out;
    }

    public String read(String ownerId, Long agentDefId, String relPath) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        if (!Files.isRegularFile(p)) {
            throw new NotFoundException("file not found: " + relPath);
        }
        try {
            long size = Files.size(p);
            if (size > MAX_READ) {
                return "(file too large to display: " + size + " bytes)";
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("read failed: " + relPath, e);
        }
    }

    public void write(String ownerId, Long agentDefId, String relPath, String content) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("write failed: " + relPath, e);
        }
    }

    /** 写 user 级共享文件(skills/ AGENTS.md MEMORY.md 等),跨 agent 共享。 */
    public void writeUser(String ownerId, String relPath, String content) {
        Path p = resolver.resolveUser(ownerId, relPath);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("user-level write failed: " + relPath, e);
        }
    }

    /** Returns true if the file existed and was removed. */
    public boolean delete(String ownerId, Long agentDefId, String relPath) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new RuntimeException("delete failed: " + relPath, e);
        }
    }

    private FileNodeVO toNode(Path p, Path root) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        if (Files.isDirectory(p)) {
            return new FileNodeVO(p.getFileName().toString(), rel, "dir", 0L);
        }
        long size;
        try { size = Files.size(p); } catch (IOException e) { size = 0L; }
        return new FileNodeVO(p.getFileName().toString(), rel, "file", size);
    }
}
