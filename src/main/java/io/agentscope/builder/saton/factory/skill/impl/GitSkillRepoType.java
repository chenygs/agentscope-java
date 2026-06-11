package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.skill.SkillRepoType;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Git-backed skill repository. Loaded via reflection so the project can run without the
 * optional {@code agentscope-extensions-skill-git-repository} dependency on classpath.
 *
 * <p>Missing dependency surfaces as {@link IllegalStateException} at instantiate time —
 * 由 spec §12.17 加的 IllegalArgumentException 处理映射到 HTTP 400 不适用；这是后端
 * 配置错误，500 比 400 更对。
 */
@Component
public class GitSkillRepoType implements SkillRepoType {

    private static final Logger log = LoggerFactory.getLogger(GitSkillRepoType.class);
    private static final String GIT_REPO_CLASS = "io.agentscope.core.skill.repository.GitSkillRepository";

    @Override
    public String type() {
        return "git";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> remoteUrl = new LinkedHashMap<>();
        remoteUrl.put("type", "string");
        remoteUrl.put("description", "git 仓库 URL");
        properties.put("remoteUrl", remoteUrl);
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put("type", "string");
        branch.put("description", "分支名（可选，默认 main）");
        properties.put("branch", branch);
        Map<String, Object> localPath = new LinkedHashMap<>();
        localPath.put("type", "string");
        localPath.put("description", "本地缓存路径（workspace-relative，可选）");
        properties.put("localPath", localPath);
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("remoteUrl"));
        return new TypeMeta(type(), "Git 仓库 skills", "从 git 拉 skill 仓库挂为 overlay（需 agentscope-extensions-skill-git-repository dep）", schema);
    }

    @Override
    public AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot) {
        String remoteUrl = stringProp(props, "remoteUrl");
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("git skill repo requires 'remoteUrl'");
        }
        String localPathStr = stringProp(props, "localPath");
        Path localPath = (localPathStr != null && !localPathStr.isBlank())
                ? workspaceRoot.resolve(localPathStr).normalize()
                : null;

        try {
            Class<?> cls = Class.forName(GIT_REPO_CLASS);
            if (localPath != null) {
                Constructor<?> ctor = cls.getConstructor(String.class, Path.class);
                return (AgentSkillRepository) ctor.newInstance(remoteUrl, localPath);
            } else {
                Constructor<?> ctor = cls.getConstructor(String.class);
                return (AgentSkillRepository) ctor.newInstance(remoteUrl);
            }
        } catch (ClassNotFoundException e) {
            log.warn("GitSkillRepository class not on classpath; add agentscope-extensions-skill-git-repository dep");
            throw new IllegalStateException("git skill repository support not installed (add agentscope-extensions-skill-git-repository dependency)", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to construct GitSkillRepository: " + e.getMessage(), e);
        }
    }

    private static String stringProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object v = props.get(key);
        return v instanceof String s ? s : null;
    }
}
