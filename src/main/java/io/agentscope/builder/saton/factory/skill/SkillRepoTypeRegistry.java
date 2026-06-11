package io.agentscope.builder.saton.factory.skill;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring-collected registry of all {@link SkillRepoType} beans. */
@Component
public class SkillRepoTypeRegistry extends ProviderRegistry<SkillRepoType> {

    public SkillRepoTypeRegistry(List<SkillRepoType> types) {
        super(types);
    }
}
