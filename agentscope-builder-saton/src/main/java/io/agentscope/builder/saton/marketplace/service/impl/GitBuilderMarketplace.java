package io.agentscope.builder.saton.marketplace.service.impl;

import io.agentscope.builder.saton.marketplace.service.BuilderMarketplace;
import io.agentscope.builder.saton.marketplace.service.MarketSkillContent;
import io.agentscope.builder.saton.marketplace.service.MarketSkillSummary;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Git-backed marketplace. Reflects {@code io.agentscope.core.skill.repository.GitSkillRepository}
 * so the project can run without the optional agentscope-extensions-skill-git-repository dependency.
 *
 * <p>Missing dependency surfaces as {@link IllegalStateException} at construction time.
 */
@Slf4j
public class GitBuilderMarketplace implements BuilderMarketplace {

    private static final String GIT_REPO_CLASS = "io.agentscope.core.skill.repository.GitSkillRepository";

    private final String id;
    private final String remoteUrl;
    private final String branch;
    private final Object repo; // GitSkillRepository instance (reflection)

    public GitBuilderMarketplace(String id, String remoteUrl, String branch,
                                  Path localPath, String skillsRoot) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("remoteUrl must not be blank");
        }
        this.id = id;
        this.remoteUrl = remoteUrl.trim();
        this.branch = (branch == null || branch.isBlank()) ? null : branch.trim();
        try {
            Class<?> cls = Class.forName(GIT_REPO_CLASS);
            Constructor<?> ctor = cls.getConstructor(
                    String.class, String.class, Path.class, String.class, boolean.class, String.class);
            this.repo = ctor.newInstance(
                    this.remoteUrl,
                    this.branch,
                    localPath,
                    "git:" + id,
                    true,
                    skillsRoot);
            // Probe: verify repo is reachable
            Method getAllSkills = cls.getMethod("getAllSkills");
            getAllSkills.invoke(this.repo);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "git skill repository support not installed (add agentscope-extensions-skill-git-repository dependency)", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "failed to initialize GitBuilderMarketplace: " + e.getMessage(), e);
        }
    }

    @Override
    public String id() { return id; }

    @Override
    public String type() { return "git"; }

    @Override
    public String displayLocation() {
        return branch != null ? remoteUrl + " @" + branch : remoteUrl;
    }

    @Override
    public List<MarketSkillSummary> list() {
        try {
            Class<?> cls = repo.getClass();
            Method getAllSkills = cls.getMethod("getAllSkills");
            List<?> skills = (List<?>) getAllSkills.invoke(repo);
            List<MarketSkillSummary> summaries = new ArrayList<>();
            for (Object skill : skills) {
                String name = (String) skill.getClass().getMethod("getName").invoke(skill);
                String desc = (String) skill.getClass().getMethod("getDescription").invoke(skill);
                summaries.add(new MarketSkillSummary(name, desc, null));
            }
            return summaries;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GitBuilderMarketplace list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public MarketSkillContent fetch(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            Class<?> cls = repo.getClass();
            Method getSkill = cls.getMethod("getSkill", String.class);
            Object skill = getSkill.invoke(repo, name.trim());
            if (skill == null) return null;

            String skillName = (String) skill.getClass().getMethod("getName").invoke(skill);
            String desc = (String) skill.getClass().getMethod("getDescription").invoke(skill);
            String content = (String) skill.getClass().getMethod("getSkillContent").invoke(skill);
            if (content == null || content.isBlank()) return null;
            @SuppressWarnings("unchecked")
            Map<String, String> resources = (Map<String, String>)
                    skill.getClass().getMethod("getResources").invoke(skill);
            return new MarketSkillContent(skillName, desc, content, resources);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "GitBuilderMarketplace fetch failed for " + name + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            repo.getClass().getMethod("close").invoke(repo);
        } catch (Exception e) {
            log.warn("failed to close git marketplace {} ({})", id, remoteUrl, e);
        }
    }
}
