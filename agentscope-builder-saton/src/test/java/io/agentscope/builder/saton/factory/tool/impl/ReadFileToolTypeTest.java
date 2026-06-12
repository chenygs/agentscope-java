package io.agentscope.builder.saton.factory.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTypeTest {

    @Test
    void typeAndDisplayName() {
        ReadFileToolType t = new ReadFileToolType();
        assertEquals("read-file", t.type());
        assertEquals("read-file", t.meta().type());
        assertNotNull(t.meta().displayName());
        assertNotNull(t.meta().schema());
    }

    @Test
    void instantiateNoBaseDir() {
        var tool = new ReadFileToolType().instantiate(null);
        assertNotNull(tool);
    }

    @Test
    void instantiateBlankBaseDirIgnored() {
        var tool = new ReadFileToolType().instantiate(Map.of("baseDir", "  "));
        assertNotNull(tool);
    }

    @Test
    void instantiateWithBaseDir() {
        var tool = new ReadFileToolType().instantiate(Map.of("baseDir", "."));
        assertNotNull(tool);
    }
}
