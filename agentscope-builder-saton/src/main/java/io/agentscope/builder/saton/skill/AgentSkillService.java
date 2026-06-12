package io.agentscope.builder.saton.skill;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.skill.SkillFactory;
import io.agentscope.builder.saton.marketplace.UserMarketplaceRegistry;
import io.agentscope.builder.saton.skill.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.dto.WorkspaceSkillVO;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.builder.saton.workspace.WorkspaceService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class AgentSkillService {

    private static final String SKILLS_DIR = "skills";

    private final AgentDefinitionRepository agentRepo;
    private final SkillFactory skillFactory;
    private final WorkspaceService workspaceService;
    private final WorkspacePathResolver workspaceResolver;
    private final UserMarketplaceRegistry marketplaceRegistry;

    public AgentSkillService(AgentDefinitionRepository agentRepo,
                             SkillFactory skillFactory,
                             WorkspaceService workspaceService,
                             WorkspacePathResolver workspaceResolver,
                             UserMarketplaceRegistry marketplaceRegistry) {
        this.agentRepo = agentRepo;
        this.skillFactory = skillFactory;
        this.workspaceService = workspaceService;
        this.workspaceResolver = workspaceResolver;
        this.marketplaceRegistry = marketplaceRegistry;
    }

    /** List skills installed in the agent's workspace skills/ directory. */
    public List<WorkspaceSkillVO> listWorkspaceSkills(String ownerId, Long agentDefId) {
        Path skillsDir = workspaceResolver.agentRoot(ownerId, agentDefId).resolve(SKILLS_DIR);
        if (!Files.isDirectory(skillsDir)) return List.of();

        File[] dirs = skillsDir.toFile().listFiles(File::isDirectory);
        if (dirs == null) return List.of();

        List<WorkspaceSkillVO> result = new ArrayList<>();
        for (File dir : dirs) {
            String name = dir.getName();
            String description = "";
            String source = "local";
            long installTime = 0L;

            Path metaFile = skillsDir.resolve(name).resolve("_install.meta.json");
            if (Files.exists(metaFile)) {
                try {
                    String metaContent = Files.readString(metaFile, java.nio.charset.StandardCharsets.UTF_8);
                    Map<String, Object> meta = JsonUtil.mapper().readValue(metaContent,
                            new TypeReference<Map<String, Object>>() {});
                    if (meta.get("source") instanceof String s) source = s;
                    if (meta.get("installedAt") instanceof String ts) {
                        try { installTime = java.time.Instant.parse(ts).toEpochMilli(); }
                        catch (Exception ignored) {}
                    }
                    description = String.valueOf(meta.getOrDefault("originalName", ""));
                } catch (Exception e) {
                    log.warn("failed to read install meta for skill {}", name, e);
                }
            }
            result.add(new WorkspaceSkillVO(name, description, source, installTime));
        }
        return result;
    }

    /** Install a skill from the agent's configured skill repository by index. */
    public WorkspaceSkillVO installFromRepository(String ownerId, Long agentDefId, InstallFromRepoReq req) {
        AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, ownerId)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));

        List<SkillRepoSpec> repos = parseSkillRepoSpecs(def.getSkillRepositoriesJson());
        if (req.repoIndex() < 0 || req.repoIndex() >= repos.size()) {
            throw new IllegalArgumentException("repoIndex out of range: " + req.repoIndex());
        }
        SkillRepoSpec spec = repos.get(req.repoIndex());

        Path workspace = workspaceResolver.agentRoot(ownerId, agentDefId);
        AgentSkillRepository repo = skillFactory.instantiate(spec.type(), spec.props(), workspace);

        AgentSkill skill = repo.getSkill(req.skillName());
        if (skill == null) {
            throw new NotFoundException("skill not found in repository: " + req.skillName());
        }

        String targetName = (req.targetName() != null && !req.targetName().isBlank())
                ? req.targetName() : skill.getName();
        validateSkillName(targetName);

        String skillDir = SKILLS_DIR + "/" + targetName;
        Path skillMarkdown = workspaceResolver.resolve(ownerId, agentDefId, skillDir + "/SKILL.md");
        boolean exists = java.nio.file.Files.exists(skillMarkdown);
        if (exists && !Boolean.TRUE.equals(req.overwrite())) {
            throw new IllegalArgumentException("workspace skill already exists: " + targetName
                    + " (set overwrite=true to replace)");
        }

        String markdown = skill.getSkillContent();
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalStateException("repository returned empty SKILL.md for: " + req.skillName());
        }
        workspaceService.write(ownerId, agentDefId, skillDir + "/SKILL.md", markdown);

        Map<String, String> resources = skill.getResources();
        if (resources != null) {
            for (Map.Entry<String, String> entry : resources.entrySet()) {
                workspaceService.write(ownerId, agentDefId, skillDir + "/" + entry.getKey(), entry.getValue());
            }
        }

        writeInstallMeta(ownerId, agentDefId, skillDir, "repository", spec.type(), skill.getName());
        return new WorkspaceSkillVO(targetName, skill.getDescription(), "repository", System.currentTimeMillis());
    }

    /** Install a skill from a user-configured marketplace. */
    public WorkspaceSkillVO installFromMarketplace(String ownerId, Long agentDefId, MarketplaceInstallReq req) {
        var mp = marketplaceRegistry.find(ownerId, req.marketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not found: " + req.marketplaceId()));

        var content = mp.fetch(req.skillName());
        if (content == null) {
            throw new NotFoundException("skill not found in marketplace: " + req.skillName());
        }

        String targetName = (req.targetName() != null && !req.targetName().isBlank())
                ? req.targetName() : content.name();
        validateSkillName(targetName);

        String skillDir = SKILLS_DIR + "/" + targetName;
        Path skillMarkdown = workspaceResolver.resolve(ownerId, agentDefId, skillDir + "/SKILL.md");
        boolean exists = java.nio.file.Files.exists(skillMarkdown);
        if (exists && !Boolean.TRUE.equals(req.overwrite())) {
            throw new IllegalArgumentException("workspace skill already exists: " + targetName);
        }

        workspaceService.write(ownerId, agentDefId, skillDir + "/SKILL.md", content.markdown());
        if (content.resources() != null) {
            for (Map.Entry<String, String> entry : content.resources().entrySet()) {
                workspaceService.write(ownerId, agentDefId, skillDir + "/" + entry.getKey(), entry.getValue());
            }
        }

        writeInstallMeta(ownerId, agentDefId, skillDir, "marketplace", mp.type(), content.name());
        return new WorkspaceSkillVO(targetName, content.description(), "marketplace", System.currentTimeMillis());
    }

    /** Delete a workspace skill (recursively removes the entire skill directory). */
    public void deleteWorkspaceSkill(String ownerId, Long agentDefId, String name) {
        validateSkillName(name);
        Path skillDir = workspaceResolver.agentRoot(ownerId, agentDefId)
                .resolve(SKILLS_DIR).resolve(name);
        if (!java.nio.file.Files.exists(skillDir)) {
            throw new NotFoundException("workspace skill not found: " + name);
        }
        try {
            java.nio.file.Files.walk(skillDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { java.nio.file.Files.deleteIfExists(p); }
                        catch (java.io.IOException ex) { log.warn("failed to delete {}", p, ex); }
                    });
        } catch (java.io.IOException e) {
            throw new RuntimeException("failed to delete workspace skill: " + name, e);
        }
    }

    private void writeInstallMeta(String ownerId, Long agentDefId, String skillDir,
                                   String source, String sourceType, String originalName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", source);
        meta.put("sourceType", sourceType);
        meta.put("originalName", originalName);
        meta.put("installedAt", Instant.now().toString());
        try {
            String json = JsonUtil.mapper().writeValueAsString(meta);
            workspaceService.write(ownerId, agentDefId, skillDir + "/_install.meta.json", json);
        } catch (Exception e) {
            log.warn("failed to write install meta for {}", skillDir, e);
        }
    }

    private void validateSkillName(String name) {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("invalid skill name: " + name);
        }
    }

    private static List<SkillRepoSpec> parseSkillRepoSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<SkillRepoSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid skill_repositories_json", e);
        }
    }
}
