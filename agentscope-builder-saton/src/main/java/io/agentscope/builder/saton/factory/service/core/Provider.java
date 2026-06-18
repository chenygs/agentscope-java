package io.agentscope.builder.saton.factory.service.core;

/**
 * 所有 SPI 接口（ModelProviderType / 未来的 ToolType / SkillRepoType / MiddlewareType / AgentType）
 * 的顶层标记接口。仅用于约束 {@link ProviderRegistry} 的泛型边界。
 */
public interface Provider {

    /** 唯一标识，前端选型用，写库时也存这个字符串。例如 "dashscope" / "openai"。 */
    String type();

    /** 给前端列表 + 表单渲染的元信息。 */
    TypeMeta meta();
}
