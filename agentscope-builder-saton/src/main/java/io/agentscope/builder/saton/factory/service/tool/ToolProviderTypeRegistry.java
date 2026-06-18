package io.agentscope.builder.saton.factory.service.tool;

import io.agentscope.builder.saton.factory.service.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolProviderTypeRegistry extends ProviderRegistry<ToolType> {

    public ToolProviderTypeRegistry(List<ToolType> providers) {
        super(providers);
    }
}
