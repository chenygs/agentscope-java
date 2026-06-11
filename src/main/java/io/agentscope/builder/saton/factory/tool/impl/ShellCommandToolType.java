package io.agentscope.builder.saton.factory.tool.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.tool.ToolType;
import io.agentscope.core.tool.coding.ShellCommandTool;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ShellCommandToolType implements ToolType {

    @Override
    public String type() {
        return "shell-cmd";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "shell-cmd",
                "Shell 命令",
                "受白名单约束的 shell 命令执行（git/ls/cat 等）",
                JsonSchemaUtil.object()
                        .field("allowedCommands", "array", false,
                                "允许执行的命令前缀列表，留空 = 用框架默认白名单")
                        .build()
        );
    }

    @Override
    public Object instantiate(Map<String, Object> props) {
        Set<String> allowed = parseAllowed(props);
        return allowed != null ? new ShellCommandTool(allowed) : new ShellCommandTool();
    }

    private static Set<String> parseAllowed(Map<String, Object> props) {
        if (props == null) return null;
        Object v = props.get("allowedCommands");
        if (!(v instanceof List<?> list) || list.isEmpty()) return null;
        Set<String> out = new HashSet<>();
        for (Object item : list) {
            if (item instanceof String s && !s.isBlank()) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }
}
