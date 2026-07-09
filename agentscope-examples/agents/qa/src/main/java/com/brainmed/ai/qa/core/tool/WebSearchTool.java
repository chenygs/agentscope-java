package com.brainmed.ai.qa.core.tool;

import com.brainmed.ai.qa.core.middleware.QaMessagePersistenceMiddleware;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 联网搜索工具：委托 {@link ResponsesApiSearcher} 调用方舟 Responses API 内置的
 * {@code web_search} 工具，平台自动处理搜索、结果解析和摘要。
 *
 * <p>模型在 ReAct 循环中按需调用此工具，获取实时互联网信息。
 * 搜索结果全文由 CopilotKit 工具调用 UI 的 RESULT 区域展示。
 */
@Slf4j
public class WebSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ResponsesApiSearcher searcher;

    public WebSearchTool(ResponsesApiSearcher searcher) {
        this.searcher = searcher;
    }

    @Tool(
            name = "web_search",
            description = """
                    在互联网上搜索实时信息，适用于查询最新新闻、时事动态、实时数据、天气、股票等需要联网获取的内容。
                    当用户的问题涉及最新资讯、时效性内容或你自身知识无法回答的实时信息时使用此工具。
                    返回搜索结果的纯文本摘要，请直接基于摘要内容回答用户问题。
                    """,
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> webSearch(
            @ToolParam(name = "query", description = "搜索关键词，应简洁准确地描述要查找的信息") String query,
            RuntimeContext ctx) {

        log.info("web_search called, query='{}'", query);

        try {
            ResponseObject resp = searcher.webSearchRaw(query);
            SearchResult result = parseResponse(resp);

            // 从原始 annotation 节点中提取 url_citation 引用
            List<SearchResult.Citation> citations = extractUrlCitations(result.getRawAnnotations());
            if (!citations.isEmpty()) {
                accumulateCitations(ctx, citations);
                log.info("web_search: accumulated {} citations", citations.size());
            }
            return Mono.just(result.getText());
        } catch (Exception e) {
            log.error("web_search failed, query='{}': {}", query, e.getMessage(), e);
            return Mono.just("联网搜索失败：" + e.getMessage() + "，请直接基于已有知识回答。");
        }
    }

    /**
     * 解析 Responses API 响应对象，提取正文文本和原始 annotation 节点。
     */
    private static SearchResult parseResponse(ResponseObject resp) {
        try {
            String json = MAPPER.writeValueAsString(resp);
            JsonNode root = MAPPER.readTree(json);
            JsonNode output = root.get("output");
            if (output == null || !output.isArray() || output.isEmpty()) {
                return new SearchResult("搜索未返回结果。", Collections.emptyList(), Collections.emptyList());
            }

            StringBuilder sb = new StringBuilder();
            List<JsonNode> annotationNodes = new ArrayList<>();

            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText(""))) continue;
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) continue;
                for (JsonNode block : content) {
                    if (!"output_text".equals(block.path("type").asText(""))) continue;
                    String text = block.path("text").asText("");
                    if (!text.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                    JsonNode annotations = block.get("annotations");
                    if (annotations != null && annotations.isArray()) {
                        annotations.forEach(annotationNodes::add);
                    }
                }
            }

            if (sb.isEmpty()) {
                return new SearchResult("搜索结果（原始）：\n" + json.substring(0, Math.min(json.length(), 3000)),
                        Collections.emptyList(), annotationNodes);
            }
            return new SearchResult(sb.toString().trim(), Collections.emptyList(), annotationNodes);
        } catch (Exception e) {
            return new SearchResult("搜索结果解析失败：" + e.getMessage(), Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 从原始 annotation 节点中提取 url_citation 类型引用。
     */
    private static List<SearchResult.Citation> extractUrlCitations(List<JsonNode> annotations) {
        List<SearchResult.Citation> citations = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (JsonNode ann : annotations) {
            if (!"url_citation".equals(ann.path("type").asText(""))) continue;

            String url = ann.path("url").asText("");
            String title = ann.path("title").asText("");
            String siteName = ann.path("site_name").asText("");
            String publishTime = ann.path("publish_time").asText("");

            String dedupeKey;
            if (!url.isEmpty()) {
                dedupeKey = url;
            } else if (!title.isEmpty() || !siteName.isEmpty()) {
                dedupeKey = siteName + "|" + publishTime + "|" + title;
            } else {
                continue;
            }
            if (!seen.add(dedupeKey)) continue;

            String displayTitle = !title.isEmpty() ? title : siteName;
            SearchResult.Citation citation = new SearchResult.Citation(citations.size() + 1, displayTitle, url);
            citation.setSource("web");
            citation.setType("external");
            citation.setSiteName(siteName);
            citation.setPublishTime(publishTime);
            citations.add(citation);
        }
        return citations;
    }

    /** 把本轮工具产生的引用累加进 RuntimeContext（key=qa.citations）。 */
    @SuppressWarnings("unchecked")
    private static void accumulateCitations(RuntimeContext ctx, List<SearchResult.Citation> add) {
        Object existing = ctx.get(QaMessagePersistenceMiddleware.CITATIONS_KEY);
        List<SearchResult.Citation> all = existing instanceof List
                ? new ArrayList<>((List<SearchResult.Citation>) existing)
                : new ArrayList<>();
        all.addAll(add);
        ctx.put(QaMessagePersistenceMiddleware.CITATIONS_KEY, all);
    }
}
