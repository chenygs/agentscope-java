package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring-collected registry of all {@link HookType} beans. */
@Component
public class HookTypeRegistry extends ProviderRegistry<HookType> {

    public HookTypeRegistry(List<HookType> types) {
        super(types);
    }
}
