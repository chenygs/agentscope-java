package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 工厂门面：屏蔽 {@link ModelProviderTypeRegistry} 的存在，提供高层 API。
 * <ul>
 *   <li>{@link #listTypes()} — 给前端的 type 目录（含 schema）</li>
 *   <li>{@link #instantiate(ModelProviderEntity)} — 给业务的"从 DB 行 → 可调 Model"</li>
 * </ul>
 */
@Service
public class ModelFactory {

    private final ModelProviderTypeRegistry registry;

    public ModelFactory(ModelProviderTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    /** 实例化。entity.type 未注册时抛 {@link NotFoundException}。 */
    public Model instantiate(ModelProviderEntity entity) {
        ModelProviderType type;
        try {
            type = registry.get(entity.getType());
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown model provider type: " + entity.getType());
        }
        return type.instantiate(entity);
    }
}
