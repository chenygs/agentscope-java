package io.agentscope.builder.saton.factory.tool;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ToolFactory {

    private final ToolProviderTypeRegistry registry;

    public ToolFactory(ToolProviderTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    public Object instantiate(String type, Map<String, Object> props) {
        ToolType t;
        try {
            t = registry.get(type);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown tool type: " + type);
        }
        return t.instantiate(props != null ? props : Map.of());
    }
}
