package io.github.chenygs.pptagent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.chenygs.pptagent.config.props.WebSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Web Search 工具 —— 通过 Serper.dev API 实现免费的 Google 搜索
 *
 * <p>注册地址: https://serper.dev (免费 2500 次/月，无需信用卡)</p>
 *
 * <p>配置项见 {@link WebSearchProperties}，对应 application.yml 中 websearch.*</p>
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public WebSearchTool(WebSearchProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds() > 0
                        ? properties.getTimeoutSeconds() : 15))
                .build();
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    @Tool(name = "web_search", description = "搜索互联网获取最新信息。"
            + "当需要查询实时信息、新闻、文档或任何不确定的内容时使用此工具。"
            + "支持中文和英文搜索。")
    public String webSearch(
            @ToolParam(name = "query", description = "搜索关键词，支持中文和英文") String query,
            @ToolParam(name = "num_results", description = "返回结果数量，默认 5，最多 10", required = false)
            Integer numResults) {

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误：websearch.api-key 未配置。请在 application.yml 中设置 websearch.api-key。";
        }

        int count = (numResults != null && numResults > 0) ? Math.min(numResults, 10) : properties.getDefaultResultCount();
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 15);

        try {
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of("q", query, "num", count));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint()))
                    .header("X-API-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug("Sending Serper.dev search request: query={}", query);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Serper.dev API error: status={}, body={}", response.statusCode(), response.body());
                return "搜索失败，HTTP " + response.statusCode() + ": " + response.body();
            }

            return formatResults(response.body(), query);

        } catch (Exception e) {
            log.error("Web search failed", e);
            return "搜索出错: " + e.getMessage();
        }
    }

    /**
     * 解析并格式化 Serper.dev 返回的 JSON 结果
     */
    private String formatResults(String json, String query) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果: ").append(query).append("\n\n");

        // 知识图谱（如果有）
        JsonNode kg = root.get("knowledgeGraph");
        if (kg != null) {
            if (kg.has("title")) {
                sb.append("📌 ").append(kg.get("title").asText());
                if (kg.has("description")) {
                    sb.append(" — ").append(kg.get("description").asText());
                }
                sb.append("\n");
            }
            if (kg.has("description")) {
                sb.append(kg.get("description").asText()).append("\n");
            }
            if (kg.has("website")) {
                sb.append("🔗 ").append(kg.get("website").asText()).append("\n");
            }
            sb.append("\n");
        }

        // 有机搜索结果
        JsonNode organic = root.get("organic");
        if (organic != null && organic.isArray()) {
            int rank = 1;
            for (JsonNode result : organic) {
                String title = result.has("title") ? result.get("title").asText() : "(无标题)";
                String link = result.has("link") ? result.get("link").asText() : "";
                String snippet = result.has("snippet") ? result.get("snippet").asText() : "";

                sb.append(rank).append(". ").append(title).append("\n");
                sb.append("   ").append(link).append("\n");
                if (!snippet.isEmpty()) {
                    sb.append("   ").append(snippet).append("\n");
                }
                sb.append("\n");
                rank++;
            }
        }

        // 相关搜索（如果有）
        JsonNode related = root.get("relatedSearches");
        if (related != null && related.isArray() && related.size() > 0) {
            sb.append("相关搜索:\n");
            for (JsonNode item : related) {
                sb.append("  • ").append(item.has("query") ? item.get("query").asText() : "").append("\n");
            }
        }

        return sb.toString();
    }
}
