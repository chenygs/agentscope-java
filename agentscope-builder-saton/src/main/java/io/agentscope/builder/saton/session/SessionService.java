package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.AgentStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 浏览 / 重置 / 整体清空一个 agent 的所有 session 历史。
 *
 * <p>实现走"扫盘"路线 —— 单人视角下 session 数量小（&lt;50/agent），没必要建索引表。
 * 落盘结构由 {@link io.agentscope.core.state.JsonFileAgentStateStore} 定义：
 * {@code <root>/<userId>/<sessionId>/<key>.json}，其中本项目的
 * {@code sessionId = "agent_" + agentDefId + "_" + sessionKey}（见
 * {@link io.agentscope.builder.saton.agent.chat.ChatService}）。
 */
@Slf4j
@Service
public class SessionService {

    private final Path root;
    @SuppressWarnings("unused") // held for parity / future use
    private final AgentStateStore stateStore;

    @Autowired
    public SessionService(@Value("${app.session.root:./data/sessions}") String rootConfig,
                          AgentStateStore stateStore) {
        this(Paths.get(rootConfig).toAbsolutePath().normalize(), stateStore);
    }

    /** Direct constructor for unit tests with a {@link org.junit.jupiter.api.io.TempDir}. */
    SessionService(Path root, AgentStateStore stateStore) {
        this.root = root;
        this.stateStore = stateStore;
    }

    public List<SessionVO> list(String ownerId, Long agentDefId) {
        Path userDir = root.resolve(ownerId);
        if (!Files.isDirectory(userDir)) return List.of();
        String prefix = "agent_" + agentDefId + "_";
        List<SessionVO> out = new ArrayList<>();
        try (Stream<Path> children = Files.list(userDir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(p -> {
                        String dirName = p.getFileName().toString();
                        String sessionKey = dirName.substring(prefix.length());
                        long mtime;
                        try {
                            mtime = Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            mtime = 0L;
                        }
                        out.add(new SessionVO(sessionKey, mtime));
                    });
        } catch (IOException e) {
            log.warn("failed to list sessions for {}/{}: {}", ownerId, agentDefId, e.getMessage());
        }
        out.sort(Comparator.comparingLong(SessionVO::lastActiveAt).reversed());
        return out;
    }

    public boolean reset(String ownerId, Long agentDefId, String sessionKey) {
        Path slot = root.resolve(ownerId).resolve("agent_" + agentDefId + "_" + sessionKey);
        if (!Files.isDirectory(slot)) return false;
        deleteRecursively(slot);
        return true;
    }

    public void purgeAgent(String ownerId, Long agentDefId) {
        Path userDir = root.resolve(ownerId);
        if (!Files.isDirectory(userDir)) return;
        String prefix = "agent_" + agentDefId + "_";
        try (Stream<Path> children = Files.list(userDir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.warn("failed to purge sessions for {}/{}: {}", ownerId, agentDefId, e.getMessage());
        }
    }

    private void deleteRecursively(Path p) {
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(child -> {
                try { Files.deleteIfExists(child); }
                catch (IOException e) { log.warn("failed to delete {}: {}", child, e.getMessage()); }
            });
        } catch (IOException e) {
            log.warn("failed to walk {}: {}", p, e.getMessage());
        }
    }
}
