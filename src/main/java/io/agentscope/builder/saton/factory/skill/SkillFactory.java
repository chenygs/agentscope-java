package io.agentscope.builder.saton.factory.skill;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Facade for instantiating {@link AgentSkillRepository} by type string + props + workspaceRoot.
 */
@Component
public class SkillFactory {

    private final SkillRepoTypeRegistry registry;

    public SkillFactory(SkillRepoTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    public AgentSkillRepository instantiate(String type,
                                            Map<String, Object> props,
                                            Path workspaceRoot) {
        SkillRepoType impl;
        try {
            impl = registry.get(type);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown skill repo type: " + type);
        }
        return impl.instantiate(props == null ? Map.of() : props, workspaceRoot);
    }
}
