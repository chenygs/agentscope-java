package io.agentscope.builder.saton.factory.core;

import java.util.Map;

/**
 * 给前端用的"类型元信息"。
 *
 * @param type        唯一标识（与 {@link Provider#type()} 一致）
 * @param displayName 给人看的名字，如 "通义千问"
 * @param description 说明文本
 * @param schema      JSON Schema-like map（fields/required/...），前端按它渲染参数表单
 */
public record TypeMeta(
        String type,
        String displayName,
        String description,
        Map<String, Object> schema
) {}
