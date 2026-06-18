package io.agentscope.builder.saton.factory.service.skill.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitSkillRepoTypeTest {

    @TempDir Path workspaceRoot;

    @Test
    void typeIsGit() {
        assertEquals("git", new GitSkillRepoType().type());
    }

    @Test
    void requiresRemoteUrl() {
        GitSkillRepoType type = new GitSkillRepoType();
        assertThrows(IllegalArgumentException.class,
                () -> type.instantiate(Map.of(), workspaceRoot));
    }

    @Test
    void missingClasspathThrowsIllegalStateWithHelpfulMessage() {
        GitSkillRepoType type = new GitSkillRepoType();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> type.instantiate(Map.of("remoteUrl", "https://example.com/x.git"), workspaceRoot));
        assertTrue(ex.getMessage().contains("git-repository"),
                "error should mention the missing dep; got: " + ex.getMessage());
    }
}
