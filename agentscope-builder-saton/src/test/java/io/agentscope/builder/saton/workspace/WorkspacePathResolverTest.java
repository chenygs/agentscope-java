package io.agentscope.builder.saton.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePathResolverTest {

    @TempDir Path root;
    WorkspacePathResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new WorkspacePathResolver(root);
    }

    @Test
    void resolveValidRelativePath() {
        Path p = resolver.resolve("alice", 7L, "notes.md");
        // 新结构:<root>/alice/agents/7/notes.md (跟 AgentScope 2.0 Harness 设计对齐)
        assertTrue(p.startsWith(root.resolve("alice").resolve("agents").resolve("7")));
        assertEquals("notes.md", p.getFileName().toString());
    }

    @Test
    void userRootIsSharedAcrossAgents() {
        // 同 user 的不同 agent 应共享 userRoot — 这正是让 MEMORY.md/AGENTS.md 跨 agent 共享的基础
        Path u = resolver.userRoot("alice");
        assertEquals(u, resolver.agentRoot("alice", 7L).getParent().getParent());
        assertEquals(u, resolver.agentRoot("alice", 99L).getParent().getParent());
    }

    @Test
    void resolveUserAllowsSharedFiles() {
        Path memory = resolver.resolveUser("alice", "MEMORY.md");
        assertTrue(memory.startsWith(root.resolve("alice")));
        assertEquals("MEMORY.md", memory.getFileName().toString());

        Path skill = resolver.resolveUser("alice", "skills/git/SKILL.md");
        assertTrue(skill.endsWith(Path.of("skills", "git", "SKILL.md")));
    }

    @Test
    void resolveUserRejectsTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveUser("alice", "../bob/MEMORY.md"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveUser("alice", null));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveUser("alice", ""));
    }

    @Test
    void resolveSupportsSubdirectories() {
        Path p = resolver.resolve("alice", 7L, "subdir/inner.txt");
        assertTrue(p.endsWith(Path.of("subdir", "inner.txt")));
    }

    @Test
    void resolveRejectsDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "../etc/passwd"));
    }

    @Test
    void resolveRejectsDeepDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "a/b/../../../../escape"));
    }

    @Test
    void resolveRejectsAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "/etc/passwd"));
    }

    @Test
    void resolveRejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, ""));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, "   "));
    }

    @Test
    void agentRootIsUniquePerOwnerAndAgent() {
        Path a = resolver.agentRoot("alice", 7L);
        Path b = resolver.agentRoot("bob",   7L);
        Path c = resolver.agentRoot("alice", 8L);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }
}
