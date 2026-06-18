package io.agentscope.builder.saton.workspace.service;

import io.agentscope.builder.saton.workspace.orm.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.orm.dto.WorkspaceSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.workspace.service.WorkspaceService;
import io.agentscope.builder.saton.workspace.service.WorkspacePathResolver;

class WorkspaceServiceTest {

    @TempDir Path root;
    WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(new WorkspacePathResolver(root));
    }

    @Test
    void writeThenReadRoundtrips() {
        service.write("alice", "ppt-agent", "notes.md", "hello");
        assertEquals("hello", service.read("alice", "ppt-agent", "notes.md"));
    }

    @Test
    void writeOverwritesExisting() {
        service.write("alice", "ppt-agent", "a.txt", "v1");
        service.write("alice", "ppt-agent", "a.txt", "v2");
        assertEquals("v2", service.read("alice", "ppt-agent", "a.txt"));
    }

    @Test
    void listIncludesCreatedFile() {
        service.write("alice", "ppt-agent", "x.txt", "X");
        service.write("alice", "ppt-agent", "sub/y.txt", "Y");
        List<FileNodeVO> nodes = service.list("alice", "ppt-agent");
        // top-level: x.txt + sub/
        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("x.txt") && n.type().equals("file")));
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("sub")   && n.type().equals("dir")));
    }

    @Test
    void deleteFileRemovesIt() {
        service.write("alice", "ppt-agent", "x.txt", "X");
        assertTrue(service.delete("alice", "ppt-agent", "x.txt"));
        assertFalse(service.delete("alice", "ppt-agent", "x.txt"));
    }

    @Test
    void readMissingFileThrows() {
        assertThrows(io.agentscope.builder.saton.common.error.NotFoundException.class,
                () -> service.read("alice", "ppt-agent", "missing.txt"));
    }

    @Test
    void summaryCountsAllFiles() {
        service.write("alice", "ppt-agent", "a.txt", "A");
        service.write("alice", "ppt-agent", "b/c.txt", "C");
        service.write("alice", "ppt-agent", "b/d.txt", "D");
        WorkspaceSummaryVO sum = service.summary("alice", "ppt-agent");
        assertEquals(3, sum.fileCount());
        assertNotNull(sum.root());
    }

    @Test
    void writeRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> service.write("alice", "ppt-agent", "../escape", "x"));
    }

    @Test
    void listReturnsEmptyForUnusedAgent() {
        assertTrue(service.list("alice", "ghost-agent").isEmpty());
    }

    @Test
    void listAtDescendsIntoSubdirectory() {
        service.write("alice", "ppt-agent", "x.txt", "X");
        service.write("alice", "ppt-agent", "sub/y.txt", "Y");
        service.write("alice", "ppt-agent", "sub/nested/z.txt", "Z");

        List<FileNodeVO> children = service.listAt("alice", "ppt-agent", "sub");
        assertEquals(2, children.size());
        // path 字段应是相对 agent root 的相对路径,而不是仅文件名
        assertTrue(children.stream().anyMatch(n -> n.name().equals("y.txt") && n.path().equals("sub/y.txt")));
        assertTrue(children.stream().anyMatch(n -> n.name().equals("nested") && n.type().equals("dir") && n.path().equals("sub/nested")));
    }

    @Test
    void listAtNullPathFallsBackToRoot() {
        service.write("alice", "ppt-agent", "a.txt", "A");
        assertEquals(service.list("alice", "ppt-agent"), service.listAt("alice", "ppt-agent", null));
        assertEquals(service.list("alice", "ppt-agent"), service.listAt("alice", "ppt-agent", ""));
    }

    @Test
    void listAtRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> service.listAt("alice", "ppt-agent", "../escape"));
    }
}
