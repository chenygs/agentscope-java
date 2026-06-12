package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalSkillRepoTypeTest {

    @TempDir Path workspaceRoot;

    @Test
    void defaultPathIsSkillsSubdir() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("skills"));
        LocalSkillRepoType type = new LocalSkillRepoType();
        AgentSkillRepository repo = type.instantiate(Map.of(), workspaceRoot);
        assertTrue(repo instanceof FileSystemSkillRepository);
    }

    @Test
    void customPathIsResolvedAgainstWorkspaceRoot() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("my-skills"));
        LocalSkillRepoType type = new LocalSkillRepoType();
        AgentSkillRepository repo = type.instantiate(Map.of("path", "my-skills"), workspaceRoot);
        assertTrue(repo instanceof FileSystemSkillRepository);
    }

    @Test
    void typeIsLocal() {
        assertEquals("local", new LocalSkillRepoType().type());
    }

    @Test
    void metaIncludesPathSchema() {
        var meta = new LocalSkillRepoType().meta();
        assertEquals("local", meta.type());
        assertNotNull(meta.schema());
    }
}
