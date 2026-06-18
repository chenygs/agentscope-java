package io.agentscope.builder.saton.factory.service.model;

import io.agentscope.builder.saton.factory.service.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring 启动期自动注入所有 {@link ModelProviderType} 实现并按 type 路由。
 */
@Component
public class ModelProviderTypeRegistry extends ProviderRegistry<ModelProviderType> {

    public ModelProviderTypeRegistry(List<ModelProviderType> providers) {
        super(providers);
    }
}
