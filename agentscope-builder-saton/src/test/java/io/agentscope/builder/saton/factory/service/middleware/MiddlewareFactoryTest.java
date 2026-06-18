package io.agentscope.builder.saton.factory.service.middleware;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.core.middleware.MiddlewareBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MiddlewareFactoryTest {

    @Autowired MiddlewareFactory factory;

    @TempDir Path activityDir;

    @Test
    void instantiateLogging() {
        MiddlewareBase mw = factory.instantiate("logging", Map.of(), activityDir);
        assertNotNull(mw);
    }

    @Test
    void instantiateAuditJsonl() {
        MiddlewareBase mw = factory.instantiate("audit-jsonl", Map.of(), activityDir);
        assertNotNull(mw);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(NotFoundException.class,
                () -> factory.instantiate("nope", Map.of(), activityDir));
    }
}
