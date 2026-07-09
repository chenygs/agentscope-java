package com.brainmed.ai.qa.config.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型主备配置。借鉴 mybatis-plus dynamic-datasource 的设计：按名声明一组模型，
 * 用 {@code primary}/{@code fallback} 指针指向主/备，运行时由 AgentScope
 * {@code ReActAgent.Builder.fallbackModel(...)} 接管回退（不引入 mybatis-plus 依赖）。
 */
@Data
@ConfigurationProperties(prefix = "agent.model")
public class ModelProperties {

    /**
     * 主模型名，对应 {@link #models} 中的某个 key。
     * 类比 dynamic-datasource 的 {@code spring.datasource.dynamic.primary}。
     */
    private String primary;

    /**
     * 备用模型名，对应 {@link #models} 中的某个 key；为空则不启用回退。
     */
    private String fallback;

    /**
     * 模型配置表，key 为模型名。
     * 类比 dynamic-datasource 的 {@code spring.datasource.dynamic.datasource.<名称>}。
     */
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    /**
     * 取主模型配置；未配置或指向不存在时 fail-fast。
     */
    public ModelConfig getPrimaryConfig() {
        if (models == null || models.isEmpty()) {
            throw new IllegalStateException("未配置任何模型（agent.models 为空）");
        }
        if (primary == null || primary.isBlank()) {
            throw new IllegalStateException("未指定主模型（agent.primary）");
        }
        ModelConfig cfg = models.get(primary);
        if (cfg == null) {
            throw new IllegalStateException("主模型 " + primary + " 在 agent.models 中不存在");
        }
        return cfg;
    }

    /**
     * 取备用模型配置；未配置 fallback 时返回 null（表示不启用回退，等价单主模型）。
     */
    public ModelConfig getFallbackConfig() {
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        if (models == null) {
            return null;
        }
        ModelConfig cfg = models.get(fallback);
        if (cfg == null) {
            throw new IllegalStateException("备用模型 " + fallback + " 在 agent.models 中不存在");
        }
        return cfg;
    }

    @Data
    public static class ModelConfig {
        /**
         * provider 类型，当前仅 openai（OpenAI 兼容端点），预留扩展
         */
        private ModelProvider provider = ModelProvider.openai;

        /**
         * API Key
         */
        private String apiKey = "";

        /**
         * API 基础 URL
         */
        private String baseUrl = "";

        /**
         * endpoint path
         */
        private String endpointPath = "";

        /**
         * 模型名称
         */
        private String modelName = "";

        /**
         * 是否流式
         */
        private boolean stream = true;

        /**
         * 上下文窗口大小（token）
         */
        private int contextWindowSize = 65536;

        /**
         * formatter：deepseek（DeepSeekFormatter），后续可扩展 openai 等
         */
        private ModelFormatter formatter;
    }
}
