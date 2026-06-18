package io.agentscope.builder.saton.factory.service.tool;

import io.agentscope.builder.saton.factory.service.core.Provider;

import java.util.Map;

/**
 * "一种工具类型"的 SPI 抽象。每个实现负责：
 * <ul>
 *   <li>声明 {@link #type()} 唯一标识（写入 agent_definition.tool_specs_json 中每元素的 type 字段）</li>
 *   <li>提供 {@link #meta()} 的 JSON schema，前端按它渲染该 tool 的参数表单</li>
 *   <li>{@link #instantiate(Map)} 用 props 构造一个工具对象 —— 可以是
 *       {@code io.agentscope.core.tool.AgentTool} 实例，也可以是带 {@code @Tool} 注解
 *       方法的 POJO。两者 {@code Toolkit.registerTool(Object)} 都接受。</li>
 * </ul>
 *
 * <p>所有实现必须是 Spring {@code @Component}，启动期被 {@link ToolProviderTypeRegistry} 收集。
 */
public interface ToolType extends Provider {

    /**
     * 用 agent 的 tool_specs 元素中的 props map 实例化工具。
     *
     * @param props 配置；可能为空或 null，实现需 null-safe
     * @return AgentTool 实例或 @Tool POJO（Toolkit 都接受）
     */
    Object instantiate(Map<String, Object> props);
}
