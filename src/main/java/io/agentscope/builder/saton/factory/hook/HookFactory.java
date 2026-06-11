package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.core.hook.Hook;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Facade for instantiating {@link Hook} by type string + props + activityDir.
 *
 * <p>Unknown hook type → {@link NotFoundException} (HTTP 404), consistent with
 * {@code ToolFactory} / {@code ModelFactory} / {@code SkillFactory} siblings.
 */
@Component
@SuppressWarnings("deprecation")
public class HookFactory {

    private final HookTypeRegistry registry;

    public HookFactory(HookTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    public Hook instantiate(String type, Map<String, Object> props, Path activityDir) {
        HookType impl;
        try {
            impl = registry.get(type);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown hook type: " + type);
        }
        return impl.instantiate(props == null ? Map.of() : props, activityDir);
    }
}
