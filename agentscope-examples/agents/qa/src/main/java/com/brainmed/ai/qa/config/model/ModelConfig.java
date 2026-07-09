package com.brainmed.ai.qa.config.model;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ModelProperties.class})
public class ModelConfig {

    /**
     * 主模型（由 {@code agent.model.primary} 指向 {@code agent.model.models} 中的某个配置）。
     * 显式设置 {@code contextWindowSize}：DeepSeek 的 modelName 不在 AgentScope 的
     * 已知窗口表中，{@code getContextWindowSize()} 默认返回 0，会使压缩无法按比例触发。
     * DeepSeek V3（deepseek-chat / deepseek-reasoner）上下文为 64K = 65536 tokens。
     */
    @Bean
    public Model primaryModel(ModelProperties cfg) {
        return buildModel(cfg.getPrimaryConfig());
    }

    public Model fallbackModel(ModelProperties cfg) {
        ModelProperties.ModelConfig fallback = cfg.getFallbackConfig();
        return fallback == null ? null : buildModel(fallback);
    }

    /**
     * 按 {@link ModelProperties.ModelConfig} 构造一个 OpenAI 兼容端点的 Model，主/备模型共用此逻辑。
     */
    private Model buildModel(ModelProperties.ModelConfig cfg) {
        switch (cfg.getProvider()) {
            case dashscope:
                DashScopeChatModel.Builder dashscope =
                        DashScopeChatModel.builder().apiKey(cfg.getApiKey()).baseUrl(cfg.getBaseUrl()).modelName(cfg.getModelName()).stream(
                                cfg.isStream());
                return dashscope.build();
            default:
                OpenAIChatModel.Builder openai = OpenAIChatModel.builder()
                        .formatter(resolveFormatter(cfg.getFormatter()))
                        .apiKey(cfg.getApiKey())
                        .baseUrl(cfg.getBaseUrl())
                        .endpointPath(cfg.getEndpointPath())
                        .modelName(cfg.getModelName())
                        .stream(cfg.isStream());
                if (cfg.getContextWindowSize() > 0) {
                    openai.contextWindowSize(cfg.getContextWindowSize());
                }
                return openai.build();
        }
    }

    /**
     * 按配置名解析 provider 专属 formatter；未配置（null/空）时返回 null，
     * 由 {@link OpenAIChatModel} 兜底为 {@link io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter}。
     * 未知值 fail-fast，避免打错字静默降级到默认 formatter。
     */
    private OpenAIChatFormatter resolveFormatter(ModelFormatter formatter) {
        if (formatter == null) {
            return null;
        }
        return switch (formatter) {
            case deepseek -> new DeepSeekFormatter();
            default -> throw new IllegalStateException("不支持的 formatter: " + formatter.name());
        };
    }
}
