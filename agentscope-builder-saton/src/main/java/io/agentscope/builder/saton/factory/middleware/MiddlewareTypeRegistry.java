package io.agentscope.builder.saton.factory.middleware;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring-collected registry of all {@link MiddlewareType} beans. */
@Component
public class MiddlewareTypeRegistry extends ProviderRegistry<MiddlewareType> {

    public MiddlewareTypeRegistry(List<MiddlewareType> types) {
        super(types);
    }
}
