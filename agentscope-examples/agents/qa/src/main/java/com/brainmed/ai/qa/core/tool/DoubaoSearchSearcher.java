package com.brainmed.ai.qa.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 豆包搜索 Custom 版客户端。
 *
 * <p>调用火山「联网搜索 Custom 版」接口（{@code /search_api/web_search}），直接获取 web 搜索结果，
 * 返回每条结果的摘要（Summary，推荐用于大模型）与来源，不经模型二次摘要。
 * 与 {@link ResponsesApiSearcher} 驱动的 web_search（Responses API 内置、平台用 doubao 生成摘要）不同：
 * <ul>
 *   <li>本类返回原始搜索结果（标题/摘要/链接），喂给主模型自行组织回答、标注引用；</li>
 *   <li>鉴权用 {@code DOUBAO_SEARCH_API_KEY}（联网搜索控制台生成），独立于方舟 API Key；</li>
 *   <li>接口地址 {@code https://open.feedcoopapi.com/search_api/web_search}。</li>
 * </ul>
 *
 * <p>用 WebClient 非阻塞调用，工具方法返回 {@link Mono}，在 agent stream（boundedElastic）
 * 上下文里被订阅，HTTP IO 跑在 reactor-netty event loop，不阻塞调用线程。
 */
public class DoubaoSearchSearcher {

    private static final Logger log = LoggerFactory.getLogger(DoubaoSearchSearcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SEARCH_PATH = "/search_api/web_search";

    private final int count;
    private final boolean needSummary;
    private final String contentFormats;
    private final boolean needContent;
    private final boolean needUrl;
    private final WebClient webClient;

    /**
     * @param baseUrl        豆包搜索服务地址，如 https://open.feedcoopapi.com
     * @param apiKey         DOUBAO_SEARCH_API_KEY（Bearer Token 认证）
     * @param count          返回结果条数 [1, 50]，默认 10
     * @param needSummary    是否返回 Summary（500~1000字，推荐用于大模型场景）
     * @param contentFormats 正文格式 text / markdown
     * @param needContent    是否仅返回有正文的结果
     * @param needUrl        是否仅返回有原文链接的结果
     */
    public DoubaoSearchSearcher(String baseUrl, String apiKey, int count, boolean needSummary,
                                String contentFormats,
                                boolean needContent, boolean needUrl) {
        this.count = count;
        this.needSummary = needSummary;
        this.contentFormats = contentFormats;
        this.needContent = needContent;
        this.needUrl = needUrl;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * 联网搜索，返回拼接后的结果文本 + 引用来源。
     *
     * @param query     搜索关键词
     * @param timeRange 发文时间范围（OneDay/OneWeek/OneMonth/OneYear/YYYY-MM-DD..YYYY-MM-DD），null/空则不限
     */
    public Mono<SearchResult> search(String query, String timeRange) {
        String body = buildBody(query, timeRange);
        log.info("Doubao search request: {}", body);
        return webClient.post()
                .uri(SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .doOnNext(raw -> log.info("Doubao search response: status={}, body={}",
                                response.statusCode(),
                                raw.length() > 2000 ? raw.substring(0, 2000) + "...(truncated)" : raw))
                        .map(this::parseResponse))
                .onErrorResume(e -> {
                    log.error("Doubao search failed, query='{}': {}", query, e.getMessage(), e);
                    return Mono.just(new SearchResult(
                            "联网搜索失败：" + e.getMessage() + "，请直接基于已有知识回答。",
                            Collections.emptyList()));
                });
    }

    private String buildBody(String query, String timeRange) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("Query", query);
        body.put("SearchType", "web");
        body.put("Count", count);
        ObjectNode filter = MAPPER.createObjectNode();
        filter.put("NeedContent", needContent);
        filter.put("NeedUrl", needUrl);
        body.set("Filter", filter);
        body.put("NeedSummary", needSummary);
        if (contentFormats != null && !contentFormats.isBlank()) {
            body.put("ContentFormats", contentFormats);
        }
        if (timeRange != null && !timeRange.isBlank()) {
            body.put("TimeRange", timeRange);
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("build doubao search body failed", e);
        }
    }

    /**
     * 解析 web_search 响应：把 WebResults 拼成带来源标注的文本，从 Title/Url 提取引用。
     *
     * <p>正文优先取 Summary（推荐用于大模型），缺失时降级 Snippet；不拼接 Content（整页正文过长易撑爆上下文）。
     * citations 按 url 去重后连续编号，与 {@link WebSearchTool} / {@link VikingKnowledgeSearcher} 风格一致。
     *
     * <p>错误信息在 ResponseMetadata.Error（CodeN/Code/Message），HTTP 状态可能仍为 200，需解析 body 判断。
     */
    private SearchResult parseResponse(String json) {
        if (json == null || json.isBlank()) {
            return new SearchResult("联网搜索返回空响应。", Collections.emptyList());
        }
        try {
            JsonNode root = MAPPER.readTree(json);

            // 错误响应：ResponseMetadata.Error.CodeN != 0（HTTP 状态可能仍为 200）
            JsonNode error = root.path("ResponseMetadata").path("Error");
            int codeN = error.path("CodeN").asInt(0);
            if (codeN != 0) {
                String code = error.path("Code").asText(String.valueOf(codeN));
                String msg = error.path("Message").asText("unknown error");
                log.warn("Doubao search error: codeN={}, code={}, message={}", codeN, code, msg);
                return new SearchResult("联网搜索失败：" + msg + "（code=" + code + "）", Collections.emptyList());
            }

            JsonNode webResults = root.path("Result").path("WebResults");
            if (!webResults.isArray() || webResults.isEmpty()) {
                return new SearchResult("联网搜索未检索到相关内容。", Collections.emptyList());
            }

            StringBuilder text = new StringBuilder();
            List<SearchResult.Citation> citations = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();

            for (JsonNode item : webResults) {
                String title = item.path("Title").asText("");
                String url = item.path("Url").asText("");
                String siteName = item.path("SiteName").asText("");
                String logoUrl = item.path("LogoUrl").asText("");
                String publishTime = item.path("PublishTime").asText("");
                // 正文：优先 Summary（推荐大模型），降级 Snippet（约200字）
                String summary = item.path("Summary").asText("");
                String snippet = item.path("Snippet").asText("");
                String content = !summary.isBlank() ? summary : snippet;
                if (content.isBlank()) {
                    continue;
                }

                String citeTitle = !title.isEmpty() ? title
                        : (!siteName.isEmpty() ? siteName : "未知来源");

                // 去重 citation：优先按 url，无 url 按 title
                String dedupeKey = !url.isEmpty() ? url : title;
                if (!dedupeKey.isEmpty() && seen.add(dedupeKey)) {
                    SearchResult.Citation citation = new SearchResult.Citation(
                            citations.size() + 1, citeTitle, url);
                    citation.setSource("web");
                    citation.setType("external");
                    citation.setContent(content);
                    citation.setSiteName(siteName);
                    citation.setLogoUrl(logoUrl);
                    citation.setPublishTime(publishTime);
                    citations.add(citation);
                }

                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append("（来源：").append(citeTitle).append("）\n").append(content);
            }

            if (text.isEmpty()) {
                return new SearchResult("联网搜索未检索到相关内容。", Collections.emptyList());
            }
            return new SearchResult(text.toString(), citations, Collections.emptyList());
        } catch (Exception e) {
            log.error("Doubao search response parse failed: {}", e.getMessage(), e);
            return new SearchResult("联网搜索结果解析失败：" + e.getMessage(), Collections.emptyList());
        }
    }

    /**
     * 独立验证用：java DoubaoSearchSearcher &lt;apiKey&gt; [query] [timeRange]
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: DoubaoSearchSearcher <apiKey> [query] [timeRange]");
            return;
        }
        String apiKey = args[0];
        String query = args.length > 1 ? args[1] : "胶质母细胞瘤最新治疗进展";
        String timeRange = args.length > 2 ? args[2] : "";  // 可选：OneDay/OneWeek/OneMonth/OneYear
        DoubaoSearchSearcher s = new DoubaoSearchSearcher(
                "https://open.feedcoopapi.com",
                apiKey, 10, true, "markdown", false, true);
        SearchResult r = s.search(query, timeRange).block();
        if (r == null) {
            System.out.println("no result");
            return;
        }
        System.out.println("===== TEXT =====");
        System.out.println(r.getText());
        System.out.println("===== CITATIONS =====");
        for (SearchResult.Citation c : r.getCitations()) {
            System.out.println("[" + c.getIndex() + "] " + c.getTitle() + "  " + c.getUrl());
        }
    }
}
