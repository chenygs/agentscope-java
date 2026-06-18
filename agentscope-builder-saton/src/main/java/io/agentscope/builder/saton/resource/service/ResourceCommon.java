package io.agentscope.builder.saton.resource.service;

import io.agentscope.builder.saton.common.crypto.SensitiveFields;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 4 套独立资源 controller/service 共用的小工具。
 */
public final class ResourceCommon {

    /**
     * 把 props JSON 中的敏感字段值替换为 {@link SensitiveFields#MASKED_VALUE}（"***"）。
     * 用于 VO 返回前端。原对象不变。
     */
    public static String maskSensitive(String propsJson) {
        if (propsJson == null) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(propsJson);
            if (root instanceof ObjectNode obj) {
                walk(obj);
                return JsonUtil.mapper().writeValueAsString(obj);
            }
            return propsJson;
        } catch (Exception e) {
            throw new IllegalStateException("mask failed", e);
        }
    }

    /**
     * Upsert 时：对 incoming props 里值 = "***" 的敏感字段，从 existing props 里捞回原值。
     * 实现"前端编辑不改密码"语义。返回合并后的 JSON。
     */
    public static String mergeKeepMasked(String incomingPropsJson, String existingPropsJson) {
        if (incomingPropsJson == null) return null;
        try {
            JsonNode in = JsonUtil.mapper().readTree(incomingPropsJson);
            if (!(in instanceof ObjectNode inObj)) return incomingPropsJson;
            if (existingPropsJson == null) return JsonUtil.mapper().writeValueAsString(inObj);
            JsonNode ex = JsonUtil.mapper().readTree(existingPropsJson);
            if (!(ex instanceof ObjectNode exObj)) return JsonUtil.mapper().writeValueAsString(inObj);
            for (String key : SensitiveFields.KEYS) {
                if (inObj.has(key) && SensitiveFields.MASKED_VALUE.equals(inObj.get(key).asString())) {
                    if (exObj.has(key)) {
                        inObj.put(key, exObj.get(key).asString());
                    } else {
                        inObj.remove(key);
                    }
                }
            }
            return JsonUtil.mapper().writeValueAsString(inObj);
        } catch (Exception e) {
            throw new IllegalStateException("merge failed", e);
        }
    }

    private static void walk(ObjectNode obj) {
        var iter = obj.properties().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (val.isObject() && val instanceof ObjectNode child) {
                walk(child);
            } else if (val.isString() && SensitiveFields.KEYS.contains(key)) {
                obj.put(key, SensitiveFields.MASKED_VALUE);
            }
        }
    }

    /** props_json 缺失时给个空对象，避免 NPE 链。 */
    public static String normalizePropsJson(String s) {
        return (s == null || s.isBlank()) ? "{}" : s;
    }

    /** Map → JSON（用于 DTO incoming）。 */
    public static String mapToJson(Map<String, Object> map) {
        try {
            return JsonUtil.mapper().writeValueAsString(map == null ? new HashMap<>() : map);
        } catch (Exception e) {
            throw new IllegalStateException("map -> json failed", e);
        }
    }

    private ResourceCommon() {}
}
