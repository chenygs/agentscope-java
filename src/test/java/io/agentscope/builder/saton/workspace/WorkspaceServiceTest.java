package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceTest {

    @TempDir Path root;
    WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(new WorkspacePathResolver(root));
    }

    @Test
    void writeThenReadRoundtrips() {
        service.write("alice", 7L, "notes.md", "hello");
        assertEquals("hello", service.read("alice", 7L, "notes.md"));
    }

    @Test
    void writeOverwritesExisting() {
        service.write("alice", 7L, "a.txt", "v1");
        service.write("alice", 7L, "a.txt", "v2");
        assertEquals("v2", service.read("alice", 7L, "a.txt"));
    }

    @Test
    void listIncludesCreatedFile() {
        service.write("alice", 7L, "x.txt", "X");
        service.write("alice", 7L, "sub/y.txt", "Y");
        List<FileNodeVO> nodes = service.list("alice", 7L);
        // top-level: x.txt + sub/
        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("x.txt") && n.type().equals("file")));
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("sub")   && n.type().equals("dir")));
    }

    @Test
    void deleteFileRemovesIt() {
        service.write("alice", 7L, "x.txt", "X");
        assertTrue(service.delete("alice", 7L, "x.txt"));
        assertFalse(service.delete("alice", 7L, "x.txt"));
    }

    @Test
    void readMissingFileThrows() {
        assertThrows(io.agentscope.builder.saton.common.error.NotFoundException.class,
                () -> service.read("alice", 7L, "missing.txt"));
    }

    @Test
    void summaryCountsAllFiles() {
        service.write("alice", 7L, "a.txt", "A");
        service.write("alice", 7L, "b/c.txt", "C");
        service.write("alice", 7L, "b/d.txt", "D");
        WorkspaceSummaryVO sum = service.summary("alice", 7L);
        assertEquals(3, sum.fileCount());
        assertNotNull(sum.root());
    }

    @Test
    void writeRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> service.write("alice", 7L, "../escape", "x"));
    }

    @Test
    void listReturnsEmptyForUnusedAgent() {
        assertTrue(service.list("alice", 999L).isEmpty());
    }
}
