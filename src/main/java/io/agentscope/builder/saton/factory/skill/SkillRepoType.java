package io.agentscope.builder.saton.factory.skill;

import io.agentscope.builder.saton.factory.core.Provider;
import io.agentscope.core.skill.repository.AgentSkillRepository;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for skill repository providers. Each implementation declares a unique {@code type()}
 * string used in {@code skill_repositories_json} entries.
 *
 * <p>The factory passes {@code workspaceRoot} so local repos can resolve relative paths
 * to the agent's workspace.
 */
public interface SkillRepoType extends Provider {

    /** Instantiate an {@link AgentSkillRepository} from props + agent workspace root. */
    AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot);
}
