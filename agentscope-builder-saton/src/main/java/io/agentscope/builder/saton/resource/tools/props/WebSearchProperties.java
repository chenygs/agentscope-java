package io.agentscope.builder.saton.resource.tools.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Search 配置属性 —— 对应 application.yml 中 websearch.* 配置项
 *
 * <p>由 {@link WebSearchTool} 使用，
 * 其他工具如果需要类似配置，可参考此模式在相同包下创建对应的 Properties 类。
 */
@ConfigurationProperties(prefix = "websearch")
public class WebSearchProperties {

    /** Serper.dev API Key */
    private String apiKey = "";

    /** Serper.dev API 端点 */
    private String endpoint = "https://google.serper.dev/search";

    /** 请求超时秒数 */
    private int timeoutSeconds = 15;

    /** 默认搜索结果数量 */
    private int defaultResultCount = 5;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getDefaultResultCount() {
        return defaultResultCount;
    }

    public void setDefaultResultCount(int defaultResultCount) {
        this.defaultResultCount = defaultResultCount;
    }
}
