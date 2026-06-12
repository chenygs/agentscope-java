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
        assertTrue(p.startsWith(root.resolve("alice").resolve("7").resolve("files")));
        assertEquals("notes.md", p.getFileName().toString());
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
