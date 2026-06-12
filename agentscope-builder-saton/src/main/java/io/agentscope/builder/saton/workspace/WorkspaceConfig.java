package io.agentscope.builder.saton.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/** 暴露 {@link WorkspacePathResolver}，根目录由 {@code app.workspace.root} 配置。 */
@Configuration
public class WorkspaceConfig {

    @Bean
    public WorkspacePathResolver workspacePathResolver(
            @Value("${app.workspace.root:./data/workspaces}") String root) {
        return new WorkspacePathResolver(Paths.get(root).toAbsolutePath().normalize());
    }
}
