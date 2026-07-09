package com.brainmed.ai.qa.core.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
 * 火山方舟 Viking 知识库检索客户端。
 *
 * <p>调用 Viking 知识库服务的 {@code /api/knowledge/collection/search_knowledge} 接口，
 * 直接检索知识库原始切片（chunks），不经过模型摘要。与 {@link ResponsesApiSearcher}
 * 的 knowledge_search（Responses API 内置、平台用 doubao 生成摘要）不同：
 * <ul>
 *   <li>本类返回原始切片内容，喂给主模型自行组织回答、标注引用（标准 RAG）；</li>
 *   <li>鉴权用 {@code VIKING_API_KEY}（方舟控制台「知识库」页面生成），与方舟 API Key 不同；</li>
 *   <li>知识库用 {@code resource_id} 标识（与 Responses API 的 knowledge_base_id 复用同一值）。</li>
 * </ul>
 *
 * <p>用 WebClient 非阻塞调用，工具方法返回 {@link Mono}，在 agent stream（boundedElastic）
 * 上下文里被订阅，HTTP IO 跑在 reactor-netty event loop，不阻塞调用线程。
 */
public class VikingKnowledgeSearcher {

    private static final Logger log = LoggerFactory.getLogger(VikingKnowledgeSearcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SEARCH_PATH = "/api/knowledge/collection/search_knowledge";

    private final String resourceId;
    private final int limit;
    private final boolean rerank;
    private final WebClient webClient;

    /**
     * @param baseUrl    Viking 知识库服务地址，如 https://api-knowledgebase.mlp.cn-beijing.volces.com
     * @param apiKey     VIKING_API_KEY（Bearer Token 认证）
     * @param resourceId 知识库唯一 id（与方舟 knowledge_base_id 复用）
     * @param limit      检索返回切片数量 [1, 1000]
     * @param rerank     是否开启 rerank 重排
     */
    public VikingKnowledgeSearcher(String baseUrl, String apiKey, String resourceId,
                                   int limit, boolean rerank) {
        this.resourceId = resourceId;
        this.limit = limit;
        this.rerank = rerank;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * 检索知识库，返回拼接后的切片文本 + 引用来源。
     */
    public Mono<SearchResult> search(String query) {
        String body = buildBody(query);
        log.info("Viking search request: {}",
                body);
        return webClient.post()
                .uri(SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + System.getProperty("VIKING_API_KEY"))
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .doOnNext(raw -> log.info("Viking search response: status={}, body={}",
                                response.statusCode(),
                                raw.length() > 2000 ? raw.substring(0, 2000) + "...(truncated)" : raw))
                        .map(this::parseResponse))
                .onErrorResume(e -> {
                    log.error("Viking search failed, query='{}': {}", query, e.getMessage(), e);
                    return Mono.just(new SearchResult(
                            "知识库检索失败：" + e.getMessage() + "，请直接基于已有知识回答。",
                            Collections.emptyList()));
                });
    }

    private String buildBody(String query) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("resource_id", resourceId);
        body.put("query", query);
        body.put("limit", limit);
        ObjectNode postProcessing = MAPPER.createObjectNode();
        postProcessing.put("rerank_switch", rerank);
        body.set("post_processing", postProcessing);

        JSONObject jsonObject = new JSONObject();
        jsonObject.set("resource_id", resourceId);
        jsonObject.set("query", query);
        jsonObject.set("limit", limit);
        jsonObject.set("post_processing", new JSONObject().set("rerank_switch", rerank));
        return jsonObject.toString();
    }

    /**
     * 解析 search_knowledge 响应：把 result_list 拼成带来源标注的文本，从 doc_info 提取引用。
     *
     * <p>正文每片前标注来源名（便于主模型语义关联），citations 按 url/doc_name 去重后连续编号，
     * 与 {@link WebSearchTool} 的引用处理风格一致，前端 CITATIONS 展示零特殊处理。
     */
    private SearchResult parseResponse(String json) {
        if (json == null || json.isBlank()) {
            return new SearchResult("知识库检索返回空响应。", Collections.emptyList());
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("unknown error");
                log.warn("Viking search non-zero code: code={}, message={}", code, msg);
                return new SearchResult("知识库检索失败：" + msg, Collections.emptyList());
            }

            JsonNode resultList = root.path("data").path("result_list");
            if (!resultList.isArray() || resultList.isEmpty()) {
                return new SearchResult("知识库未检索到相关内容。", Collections.emptyList());
            }

            StringBuilder text = new StringBuilder();
            List<SearchResult.Citation> citations = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();

            for (JsonNode item : resultList) {
                String content = item.path("content").asText("");
                if (content.isBlank()) {
                    continue;
                }

                JsonNode docInfo = item.path("doc_info");
                String docName = docInfo.path("doc_name").asText("");
                String title = docInfo.path("title").asText("");
                String url = docInfo.path("url").asText("");
                String citeTitle = !docName.isEmpty() ? docName
                        : (!title.isEmpty() ? title : "未知来源");

                // 去重 citation：优先按 url，无 url 按 doc_name
                String dedupeKey = !url.isEmpty() ? url
                        : (!docName.isEmpty() ? docName : title);
                if (!dedupeKey.isEmpty() && seen.add(dedupeKey)) {
                    citations.add(new SearchResult.Citation(
                            citations.size() + 1, citeTitle, url));
                }

                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append("（来源：").append(citeTitle).append("）\n").append(content);
            }

            if (text.isEmpty()) {
                return new SearchResult("知识库未检索到相关内容。", Collections.emptyList());
            }
            return new SearchResult(text.toString(), citations, Collections.emptyList());
        } catch (Exception e) {
            log.error("Viking search response parse failed: {}", e.getMessage(), e);
            return new SearchResult("知识库检索结果解析失败：" + e.getMessage(), Collections.emptyList());
        }
    }

    /**
     * 独立验证用：java VikingKnowledgeSearcher &lt;apiKey&gt; &lt;resourceId&gt; [query]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: VikingKnowledgeSearcher <apiKey> <resourceId> [query]");
            return;
        }
        String apiKey = args[0];
        String resourceId = args[1];
        String query = args.length > 2 ? args[2] : "GBM是什么";
        VikingKnowledgeSearcher s = new VikingKnowledgeSearcher(
                "https://api-knowledgebase.mlp.cn-beijing.volces.com",
                apiKey, resourceId, 10, false);
        SearchResult r = s.search(query).block();
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
