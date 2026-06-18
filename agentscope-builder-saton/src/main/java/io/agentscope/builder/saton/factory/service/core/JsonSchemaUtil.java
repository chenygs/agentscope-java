package io.agentscope.builder.saton.factory.service.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简的 JSON Schema builder，专门拼 type=object + properties + required 的扁平结构。
 * 输出形状（用作 {@link TypeMeta#schema()}）：
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "apiKey":    { "type": "string", "description": "...", "secret": true },
 *     "modelName": { "type": "string", "description": "..." }
 *   },
 *   "required": ["apiKey", "modelName"]
 * }
 * }</pre>
 * 前端可用 react-jsonschema-form 之类的库直接渲染。
 */
public final class JsonSchemaUtil {

    public static Builder object() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        public Builder field(String name, String type, boolean required, String description) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("type", type);
            if (description != null && !description.isBlank()) {
                spec.put("description", description);
            }
            properties.put(name, spec);
            if (required) {
                this.required.add(name);
            }
            return this;
        }

        /** 加一个"敏感字段"标记（前端把它渲染成 password input；后端配合 SensitiveFields 做加密）。 */
        public Builder secretField(String name, boolean required, String description) {
            field(name, "string", required, description);
            properties.get(name).put("secret", true);
            return this;
        }

        public Map<String, Object> build() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("type", "object");
            root.put("properties", properties);
            root.put("required", List.copyOf(required));
            return root;
        }
    }

    private JsonSchemaUtil() {}
}
