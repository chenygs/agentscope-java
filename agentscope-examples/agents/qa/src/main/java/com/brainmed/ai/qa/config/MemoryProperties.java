package com.brainmed.ai.qa.config;

import io.agentscope.core.memory.mem0.Mem0ApiType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * memory.* 配置：控制长期记忆后端的切换与参数。
 *
 * <p>对应 application.yml 的 {@code memory} 段：
 * <ul>
 *   <li>{@code provider}：切换后端（mem0 / viking）；</li>
 *   <li>{@code mem0}：火山引擎托管 Mem0（Platform 版）参数；</li>
 *   <li>{@code viking}：Viking 记忆库 REST API 参数。</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    private boolean enabled = false;

    /**
     * 长期记忆后端：mem0 | viking
     */
    private String provider = "mem0";

    private Mem0 mem0 = new Mem0();
    private Viking viking = new Viking();

    @Data
    public static class Mem0 {
        private String apiKey = "";
        private String apiBaseUrl = "https://api.mem0.ai";
        /**
         * SDK 的 API 类型：
         * <ul>
         *   <li>{@code platform}：Mem0 开源云端 / 火山托管数据面，Authorization: Token 认证</li>
         *   <li>{@code self_hosted}：自部署 Mem0，X-API-Key 认证</li>
         * </ul>
         * 仅 use-sdk=true 时生效。
         */
        private Mem0ApiType apiType = Mem0ApiType.PLATFORM;
        /**
         * 是否使用 AgentScope SDK 封装的 Mem0LongTermMemory。
         * <ul>
         *   <li>{@code true}：使用 SDK（简洁但 retrieve 静默吞错，record 无法传 assistant 回复）</li>
         *   <li>{@code false}：使用自研 HTTP 直连（日志完整，user+assistant 完整写入）</li>
         * </ul>
         */
        private boolean useSdk = false;
    }

    @Data
    public static class Viking {
        private String baseUrl = "https://api-knowledgebase.mlp.cn-beijing.volces.com";
        private String apiKey = "";
        private String collectionName = "qa";
        private String projectName = "default";
        private int searchLimit = 10;
    }
}
