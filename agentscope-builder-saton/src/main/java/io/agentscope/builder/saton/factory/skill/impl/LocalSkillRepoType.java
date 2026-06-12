package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.skill.SkillRepoType;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-filesystem skill repository under {@code <workspaceRoot>/<path>}. {@code path} defaults
 * to {@code "skills"} when omitted from props.
 */
@Component
public class LocalSkillRepoType implements SkillRepoType {

    private static final String DEFAULT_PATH = "skills";

    @Override
    public String type() {
        return "local";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> pathField = new LinkedHashMap<>();
        pathField.put("type", "string");
        pathField.put("description", "workspace-relative directory (default: skills)");
        properties.put("path", pathField);
        schema.put("properties", properties);
        return new TypeMeta(type(), "本地 skills 目录", "挂载 workspace 子目录作为 skill overlay", schema);
    }

    @Override
    public AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot) {
        String relPath = optionalString(props, "path", DEFAULT_PATH);
        Path dir = workspaceRoot.resolve(relPath).normalize();
        return new FileSystemSkillRepository(dir);
    }

    private static String optionalString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object v = props.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
