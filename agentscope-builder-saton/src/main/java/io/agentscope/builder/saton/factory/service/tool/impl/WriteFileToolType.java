package io.agentscope.builder.saton.factory.service.tool.impl;

import io.agentscope.builder.saton.factory.service.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.tool.ToolType;
import io.agentscope.core.tool.file.WriteFileTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WriteFileToolType implements ToolType {

    @Override
    public String type() {
        return "write-file";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "write-file",
                "写文件",
                "创建/修改文件内容，可选限定 baseDir",
                JsonSchemaUtil.object()
                        .field("baseDir", "string", false, "限定可写的根目录，留空 = 无限制")
                        .build()
        );
    }

    @Override
    public Object instantiate(Map<String, Object> props) {
        String baseDir = ReadFileToolType.optionalString(props, "baseDir");
        return baseDir != null ? new WriteFileTool(baseDir) : new WriteFileTool();
    }
}
