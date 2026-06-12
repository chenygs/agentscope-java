package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.core.hook.Hook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
@SpringBootTest
class HookFactoryTest {

    @Autowired HookFactory factory;

    @TempDir Path activityDir;

    @Test
    void instantiateLogging() {
        Hook hook = factory.instantiate("logging", Map.of(), activityDir);
        assertNotNull(hook);
    }

    @Test
    void instantiateAuditJsonl() {
        Hook hook = factory.instantiate("audit-jsonl", Map.of(), activityDir);
        assertNotNull(hook);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(NotFoundException.class,
                () -> factory.instantiate("nope", Map.of(), activityDir));
    }
}
