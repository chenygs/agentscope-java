package io.agentscope.builder.saton.factory.service.tool;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ToolFactoryTest {

    @Autowired ToolFactory factory;

    @Test
    void allThreeBuiltinTypesRegistered() {
        Set<String> names = factory.listTypes().stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("read-file", "write-file", "shell-cmd")),
                "missing builtin tool types; got " + names);
    }

    @Test
    void schemaShapeWellFormed() {
        for (TypeMeta t : factory.listTypes()) {
            assertNotNull(t.displayName());
            assertNotNull(t.description());
            assertNotNull(t.schema());
            assertEquals("object", t.schema().get("type"));
            assertNotNull(t.schema().get("properties"));
            assertNotNull(t.schema().get("required"));
        }
    }

    @Test
    void instantiateReadFileWithBaseDir() {
        Object tool = factory.instantiate("read-file", Map.of("baseDir", "."));
        assertNotNull(tool);
        assertTrue(tool.getClass().getSimpleName().contains("ReadFile"));
    }

    @Test
    void instantiateReadFileWithoutPropsUsesDefault() {
        Object tool = factory.instantiate("read-file", null);
        assertNotNull(tool);
        assertTrue(tool.getClass().getSimpleName().contains("ReadFile"));
    }

    @Test
    void instantiateShellWithAllowedCommands() {
        Object tool = factory.instantiate("shell-cmd",
                Map.of("allowedCommands", List.of("ls", "cat")));
        assertNotNull(tool);
        assertTrue(tool.getClass().getSimpleName().contains("Shell"));
    }

    @Test
    void unknownTypeThrowsNotFound() {
        assertThrows(NotFoundException.class,
                () -> factory.instantiate("nope-such-tool", Map.of()));
    }
}
