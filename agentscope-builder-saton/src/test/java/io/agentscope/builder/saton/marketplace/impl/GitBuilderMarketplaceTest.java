package io.agentscope.builder.saton.marketplace.impl;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GitBuilderMarketplaceTest {

    @Test
    void nullIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitBuilderMarketplace(null, "https://example.com/repo.git", null, null, null));
    }

    @Test
    void nullRemoteUrlThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitBuilderMarketplace("test", null, null, null, null));
    }

    @Test
    void missingClasspathThrowsIseWithHint() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new GitBuilderMarketplace("test", "https://example.com/repo.git", null, null, null));
        assertTrue(ex.getMessage().contains("git-repository"),
                "error must mention git-repository dep; got: " + ex.getMessage());
    }
}
