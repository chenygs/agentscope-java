package io.agentscope.builder.saton.factory.middleware;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.core.middleware.MiddlewareBase;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Facade for instantiating {@link MiddlewareBase} by type string + props + activityDir.
 *
 * <p>Unknown middleware type → {@link NotFoundException} (HTTP 404), consistent with
 * {@code ToolFactory} / {@code ModelFactory} / {@code SkillFactory} siblings.
 */
@Component
public class MiddlewareFactory {

    private final MiddlewareTypeRegistry registry;

    public MiddlewareFactory(MiddlewareTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    public MiddlewareBase instantiate(String type, Map<String, Object> props, Path activityDir) {
        MiddlewareType impl;
        try {
            impl = registry.get(type);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown middleware type: " + type);
        }
        return impl.instantiate(props == null ? Map.of() : props, activityDir);
    }
}
