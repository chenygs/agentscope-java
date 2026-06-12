package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelFactoryTest {

    @Autowired ModelFactory factory;

    @Test
    void allFiveBuiltinTypesRegistered() {
        List<TypeMeta> types = factory.listTypes();
        Set<String> names = types.stream().map(TypeMeta::type).collect(java.util.stream.Collectors.toSet());
        assertTrue(names.containsAll(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai")),
                "missing some builtin types; got " + names);
    }

    @Test
    void typesAreSortedAlphabetically() {
        List<TypeMeta> types = factory.listTypes();
        List<String> sorted = types.stream().map(TypeMeta::type).sorted().toList();
        assertEquals(sorted, types.stream().map(TypeMeta::type).toList());
    }

    @Test
    void schemaContainsExpectedFieldsForDashScope() {
        TypeMeta dash = factory.listTypes().stream()
                .filter(m -> "dashscope".equals(m.type())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var props = (java.util.Map<String, Object>) dash.schema().get("properties");
        assertTrue(props.containsKey("apiKey"));
        assertTrue(props.containsKey("modelName"));
        assertTrue(props.containsKey("baseUrl"));
        @SuppressWarnings("unchecked")
        var required = (List<String>) dash.schema().get("required");
        assertTrue(required.contains("apiKey"));
        assertTrue(required.contains("modelName"));
        assertFalse(required.contains("baseUrl"));
    }

    @Test
    void ollamaSchemaHasNoApiKey() {
        TypeMeta ol = factory.listTypes().stream()
                .filter(m -> "ollama".equals(m.type())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var props = (java.util.Map<String, Object>) ol.schema().get("properties");
        assertFalse(props.containsKey("apiKey"));
        assertTrue(props.containsKey("modelName"));
    }

    @Test
    void instantiateDashScopeReturnsModel() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setId(99L);
        e.setOwnerId("test");
        e.setName("t");
        e.setType("dashscope");
        e.setPropsJson("{\"apiKey\":\"sk-not-real\",\"modelName\":\"qwen-max\"}");
        Model m = factory.instantiate(e);
        assertNotNull(m);
        // 不发请求；只验对象创建成功 + 类型正确
        assertTrue(m.getClass().getSimpleName().contains("DashScope"));
    }

    @Test
    void instantiateMissingRequiredFieldThrows() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("openai");
        e.setPropsJson("{\"modelName\":\"gpt-4o\"}");  // apiKey 缺
        assertThrows(IllegalArgumentException.class, () -> factory.instantiate(e));
    }

    @Test
    void instantiateUnknownTypeThrowsNotFound() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("nope-such-provider");
        e.setPropsJson("{}");
        assertThrows(NotFoundException.class, () -> factory.instantiate(e));
    }

    @Test
    void instantiateOllamaDoesNotRequireApiKey() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("ollama");
        e.setPropsJson("{\"modelName\":\"llama3.2\"}");
        Model m = factory.instantiate(e);
        assertNotNull(m);
        assertTrue(m.getClass().getSimpleName().contains("Ollama"));
    }
}
