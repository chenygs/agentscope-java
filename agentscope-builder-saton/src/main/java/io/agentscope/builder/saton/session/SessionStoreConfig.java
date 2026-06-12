package io.agentscope.builder.saton.session;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 暴露进程级 {@link AgentStateStore}，由 ReActAgent 用作 {@code agent_state} 持久化后端。
 *
 * <p>根目录由 {@code app.session.root} 配置（默认 {@code ./data/sessions}）；
 * 测试 profile 通过 {@code application.yml} 覆盖到临时目录。
 */
@Configuration
public class SessionStoreConfig {

    @Bean
    public AgentStateStore agentStateStore(
            @Value("${app.session.root:./data/sessions}") String root) {
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        return new JsonFileAgentStateStore(rootPath);
    }
}
