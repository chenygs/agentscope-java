package io.agentscope.builder.saton.factory.tool;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolProviderTypeRegistry extends ProviderRegistry<ToolType> {

    public ToolProviderTypeRegistry(List<ToolType> providers) {
        super(providers);
    }
}
