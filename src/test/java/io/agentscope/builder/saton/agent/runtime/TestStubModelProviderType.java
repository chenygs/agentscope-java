package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

/**
 * 测试专用 ModelProviderType。在 src/test/java 下，仅被 @SpringBootTest 启用上下文时
 * 自动扫到并注册进 ModelProviderTypeRegistry，type 名 "test-stub"。
 */
@Component
public class TestStubModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "test-stub";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "test-stub",
                "Test Stub Model",
                "Returns canned response, used in tests only",
                JsonSchemaUtil.object().build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        return new TestStubModel();
    }
}
