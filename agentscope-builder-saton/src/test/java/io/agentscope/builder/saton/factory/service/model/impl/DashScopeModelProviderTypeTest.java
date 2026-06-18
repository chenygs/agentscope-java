package io.agentscope.builder.saton.factory.service.model.impl;

import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeModelProviderTypeTest {

    @Test
    void typeAndDisplayName() {
        DashScopeModelProviderType t = new DashScopeModelProviderType();
        assertEquals("dashscope", t.type());
        assertEquals("dashscope", t.meta().type());
        assertNotNull(t.meta().displayName());
        assertNotNull(t.meta().schema());
    }

    @Test
    void instantiateWithEmptyPropsThrows() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("dashscope");
        e.setPropsJson("{}");
        assertThrows(IllegalArgumentException.class,
                () -> new DashScopeModelProviderType().instantiate(e));
    }

    @Test
    void instantiateWithBaseUrlOverride() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("dashscope");
        e.setPropsJson("""
                {"apiKey":"sk-x","modelName":"qwen-max","baseUrl":"https://my-gw"}""");
        var m = new DashScopeModelProviderType().instantiate(e);
        assertNotNull(m);
    }
}
