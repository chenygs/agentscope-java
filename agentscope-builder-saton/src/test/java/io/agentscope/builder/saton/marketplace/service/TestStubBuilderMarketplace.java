package io.agentscope.builder.saton.marketplace.service;

import java.util.List;
import java.util.Map;
import io.agentscope.builder.saton.marketplace.service.MarketSkillSummary;
import io.agentscope.builder.saton.marketplace.service.BuilderMarketplace;
import io.agentscope.builder.saton.marketplace.service.MarketSkillContent;

/**
 * 测试专用 BuilderMarketplace。仅被 @SpringBootTest 启用上下文时自动扫到，
 * type 名 "test-stub"。不需要外部连接，list 返回预设的 2 个 skill。
 */
public class TestStubBuilderMarketplace implements BuilderMarketplace {

    private final String id;

    public TestStubBuilderMarketplace(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "test-stub";
    }

    @Override
    public String displayLocation() {
        return "test-stub://local";
    }

    @Override
    public List<MarketSkillSummary> list() {
        return List.of(
                new MarketSkillSummary("hello-skill", "A test stub skill", "1.0"),
                new MarketSkillSummary("echo-skill", "Echoes input back", "1.0")
        );
    }

    @Override
    public MarketSkillContent fetch(String name) {
        if ("hello-skill".equals(name)) {
            return new MarketSkillContent("hello-skill", "A test stub skill",
                    "# Hello Skill\nSays hello.", Map.of());
        }
        if ("echo-skill".equals(name)) {
            return new MarketSkillContent("echo-skill", "Echoes input back",
                    "# Echo Skill\nEchoes input.", Map.of());
        }
        return null;
    }

    @Override
    public void close() {
        // no-op
    }
}
