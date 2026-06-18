package io.agentscope.builder.saton.factory.service.skill;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillFactoryTest {

    @Autowired SkillFactory factory;

    @TempDir Path workspaceRoot;

    @Test
    void instantiateLocal() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("skills"));
        AgentSkillRepository repo = factory.instantiate("local", Map.of(), workspaceRoot);
        assertNotNull(repo);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(NotFoundException.class,
                () -> factory.instantiate("never-existed", Map.of(), workspaceRoot));
    }
}
