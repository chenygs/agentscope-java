package io.agentscope.builder.saton.factory.tool.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.tool.ToolType;
import io.agentscope.core.tool.file.ReadFileTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReadFileToolType implements ToolType {

    @Override
    public String type() {
        return "read-file";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "read-file",
                "读文件",
                "读取本地文件内容，可选限定 baseDir 防止路径穿越",
                JsonSchemaUtil.object()
                        .field("baseDir", "string", false, "限定可读的根目录，留空 = 无限制")
                        .build()
        );
    }

    @Override
    public Object instantiate(Map<String, Object> props) {
        String baseDir = optionalString(props, "baseDir");
        return baseDir != null ? new ReadFileTool(baseDir) : new ReadFileTool();
    }

    /* shared helper — same pattern as ModelProviderType impls */
    static String optionalString(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object v = props.get(key);
        if (v instanceof String s && !s.isBlank()) return s;
        return null;
    }
}
