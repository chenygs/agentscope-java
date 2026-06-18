package io.agentscope.builder.saton.marketplace.service.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NacosBuilderMarketplaceTest {

    @Test
    void nullIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new NacosBuilderMarketplace(null, "http://nacos:8848", null, null, null, null, null));
    }

    @Test
    void nullServerAddrThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new NacosBuilderMarketplace("test", null, null, null, null, null, null));
    }

    @Test
    void missingClasspathThrowsIseWithHint() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new NacosBuilderMarketplace("test", "http://nacos:8848", null, null, null, null, null));
        assertTrue(ex.getMessage().contains("nacos"),
                "error must mention nacos dep; got: " + ex.getMessage());
    }
}
