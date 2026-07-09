package com.brainmed.ai.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * search.* 配置：控制四组搜索工具的启用与参数。
 *
 * <p>对应 application.yml 的 {@code search} 段，每组按 {@code enabled} 独立激活：
 * <ul>
 *   <li>{@code web} / {@code knowledge}：基于方舟 Responses API 内置搜索；</li>
 *   <li>{@code viking}：Viking search_knowledge 直接检索知识库原始切片；</li>
 *   <li>{@code doubao}：豆包搜索 Custom 版直接获取 web 搜索结果。</li>
 * </ul>
 *
 * <p>字段默认值与原 {@code @Value("${...:default}")} 的默认值保持一致，
 * application.yml 缺省时回退到这些默认值。
 */
@Data
@ConfigurationProperties(prefix = "agent.search")
public class SearchProperties {

    private Web web = new Web();
    private Knowledge knowledge = new Knowledge();
    private Responses responses = new Responses();
    private Viking viking = new Viking();
    private Doubao doubao = new Doubao();

    @Data
    public static class Web {
        private boolean enabled = true;
    }

    @Data
    public static class Knowledge {
        private boolean enabled = true;
    }

    @Data
    public static class Responses {
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String knowledgeBaseId = "";
    }

    @Data
    public static class Viking {
        private boolean enabled = true;
        private String baseUrl = "https://api-knowledgebase.mlp.cn-beijing.volces.com";
        private String apiKey = "";
        private String resourceId = "";
        private int limit = 10;
        private boolean rerank = false;
    }

    @Data
    public static class Doubao {
        private boolean enabled = false;
        private String baseUrl = "https://open.feedcoopapi.com";
        private String apiKey = "";
        private int count = 10;
        private boolean needSummary = true;
        private String contentFormats = "markdown";
        private boolean needContent = false;
        private boolean needUrl = true;
    }
}
