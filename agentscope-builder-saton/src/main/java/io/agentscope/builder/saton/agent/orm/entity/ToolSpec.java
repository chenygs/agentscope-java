package io.agentscope.builder.saton.agent.orm.entity;

import java.util.Map;

/**
 * Agent 配置中“一个工具”的描述：type + 该 type 私有 props。
 * 跟 spec §5.3 定义的 tool_specs_json 元素一一对应。
 */
public record ToolSpec(String type, Map<String, Object> props) {}
