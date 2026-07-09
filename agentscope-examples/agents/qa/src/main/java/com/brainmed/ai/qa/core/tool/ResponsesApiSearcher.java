package com.brainmed.ai.qa.core.tool;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;
import com.volcengine.ark.runtime.model.responses.event.StreamEvent;
import com.volcengine.ark.runtime.model.responses.item.ItemEasyMessage;
import com.volcengine.ark.runtime.model.responses.item.MessageContent;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.model.responses.tool.ResponsesTool;
import com.volcengine.ark.runtime.model.responses.tool.ToolWebSearch;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 火山方舟 Responses API 搜索客户端（基于官方 SDK）。
 *
 * <p>通过 {@link ArkService} 调用 Responses API，利用平台内置的 {@code web_search} 和
 * {@code knowledge_search} 工具执行搜索。平台自动处理搜索、结果解析和摘要生成。
 */
public class ResponsesApiSearcher {

    private static final Logger log = LoggerFactory.getLogger(ResponsesApiSearcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final String knowledgeResourceId;
    private final ArkService arkService;

    /** knowledge_search beta 阶段必须携带的 Header */
    private static final Map<String, String> KNOWLEDGE_SEARCH_HEADERS = Map.of(
            "Content-Type", "application/json",
            "ark-beta-knowledge-search", "true",
            "Authorization", "Bearer 568e574a-36bd-43c5-a5da-6f0b0a31ff8c"
    );

    /**
     * @param baseUrl         方舟 API 基础 URL，如 "https://ark.cn-beijing.volces.com/api/v3"
     * @param apiKey          方舟 API Key（Bearer Token 认证）
     * @param modelName       模型名称（搜索请求使用的模型，仅支持豆包系列）
     * @param knowledgeResourceId 知识库 ID（可为 null，仅 knowledge_search 需要）
     */
    public ResponsesApiSearcher(String baseUrl, String apiKey, String modelName, String knowledgeResourceId) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.knowledgeResourceId = knowledgeResourceId;
        this.arkService = ArkService.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 执行联网搜索（web_search），返回 SDK 原始响应对象。
     */
    public ResponseObject webSearchRaw(String query) throws Exception {
        ToolWebSearch tool = ToolWebSearch.builder().limit(30L).build();
        return callResponsesApi(query, Collections.singletonList(tool), null);
    }

    /**
     * 执行知识库搜索（knowledge_search），返回 SDK 原始响应对象。
     */
    public ResponseObject knowledgeSearchRaw(String query) throws Exception {
        Map<String, Object> tool = new java.util.LinkedHashMap<>();
        tool.put("type", "knowledge_search");
        tool.put("limit", 30);
        if (knowledgeResourceId != null && !knowledgeResourceId.isBlank()) {
            tool.put("knowledge_resource_id", knowledgeResourceId);
        }
        ResponsesTool knowledgeTool = MAPPER.convertValue(tool, ResponsesTool.class);
        return callResponsesApi(query, Collections.singletonList(knowledgeTool), KNOWLEDGE_SEARCH_HEADERS);
    }

    /**
     * 流式执行联网搜索，每收到文本增量时回调 {@code textDeltaCallback}。
     * 用于在工具执行期间通过侧通道向前端推送搜索进度。
     */
    public SearchResult webSearchStreaming(String query, Consumer<String> textDeltaCallback) throws Exception {
        ToolWebSearch tool = ToolWebSearch.builder().limit(30L).build();
        return streamCallResponsesApi(query, Collections.singletonList(tool), null, textDeltaCallback);
    }

    /**
     * 流式执行知识库搜索，每收到文本增量时回调 {@code textDeltaCallback}。
     */
    public SearchResult knowledgeSearchStreaming(String query, Consumer<String> textDeltaCallback) throws Exception {
        Map<String, Object> tool = new java.util.LinkedHashMap<>();
        tool.put("type", "knowledge_search");
        tool.put("limit", 30);
        if (knowledgeResourceId != null && !knowledgeResourceId.isBlank()) {
            tool.put("knowledge_resource_id", knowledgeResourceId);
        }
        ResponsesTool knowledgeTool = MAPPER.convertValue(tool, ResponsesTool.class);
        return streamCallResponsesApi(query, Collections.singletonList(knowledgeTool), KNOWLEDGE_SEARCH_HEADERS, textDeltaCallback);
    }

    /**
     * 流式调用 Responses API，每收到 {@code response.output_text.delta} 事件时回调。
     * 最终返回与同步调用等价的 {@link SearchResult}。
     */
    private SearchResult streamCallResponsesApi(String query, List<ResponsesTool> tools,
                                                 Map<String, String> customHeaders,
                                                 Consumer<String> textDeltaCallback) throws Exception {
        CreateResponsesRequest request = buildRequest(query, tools, true);
        log.info("Responses API stream request: model={}, tools={}", modelName, tools);

        Flowable<StreamEvent> flowable = (customHeaders != null && !customHeaders.isEmpty())
                ? arkService.streamResponse(request, customHeaders)
                : arkService.streamResponse(request);

        // 阻塞订阅（工具方法本身运行在 boundedElastic 线程，不阻塞 Netty event loop）
        StringBuilder textAccum = new StringBuilder();
        List<JsonNode> annotationNodes = new ArrayList<>();

        flowable.blockingSubscribe(
                event -> {
                    JsonNode node = toTree(event);
                    String type = node.path("type").asText("");
                    log.debug("Stream event: {}", type);

                    // 文本增量 → 回调推送 + 累加
                    if (type.contains("output_text.delta")) {
                        String delta = node.path("delta").asText("");
                        if (!delta.isEmpty()) {
                            textAccum.append(delta);
                            if (textDeltaCallback != null) {
                                textDeltaCallback.accept(delta);
                            }
                        }
                    }
                    // annotation 事件 → 收集引用
                    else if (type.contains("annotation_added")) {
                        JsonNode annotation = node.get("annotation");
                        if (annotation != null) {
                            annotationNodes.add(annotation);
                        }
                    }
                },
                error -> log.error("Responses API stream error: {}", error.getMessage(), error)
        );

        // 不再在此处提取引用，原始 annotation 节点随 SearchResult 返回，由各 Tool 类自行处理
        String text = textAccum.toString().trim();
        if (text.isEmpty()) {
            text = "搜索未返回结果。";
        }
        return new SearchResult(text, Collections.emptyList(), annotationNodes);
    }

    /**
     * 调用 Responses API（同步）并返回 SDK 原始响应对象。
     */
    private ResponseObject callResponsesApi(String query, List<ResponsesTool> tools, Map<String, String> customHeaders) throws Exception {
        CreateResponsesRequest request = buildRequest(query, tools, false);
        log.info("Responses API request: model={}, tools={}, headers={}", modelName, tools, customHeaders);

        ResponseObject resp = (customHeaders != null && !customHeaders.isEmpty())
                ? arkService.createResponse(request, customHeaders)
                : arkService.createResponse(request);
        log.info("Responses API response received: \n {}", JSONUtil.toJsonPrettyStr(resp));
        return resp;
    }

    /**
     * 构建 Responses API 请求体。
     *
     * @param streaming 是否开启流式（SSE），流式调用时必须为 true
     */
    private CreateResponsesRequest buildRequest(String query, List<ResponsesTool> tools, boolean streaming) {
        return CreateResponsesRequest.builder()
                .model(modelName)
                .stream(streaming)
                .input(ResponsesInput.builder()
                        .addListItem(ItemEasyMessage.builder()
                                .role("user")
                                .content(MessageContent.builder()
                                        .addListItem(InputContentItemText.builder()
                                                .text("请搜索以下内容并返回搜索结果：\n" + query)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .tools(tools)
                .build();
    }

    /**
     * 把 StreamEvent 转为 JsonNode，便于统一读取字段。
     * SDK 的 StreamEvent 子类 getter 命名不完全统一，用 JSON 中转最安全。
     */
    private JsonNode toTree(StreamEvent event) {
        return MAPPER.valueToTree(event);
    }


    /**
     * 打印完整的 curl 命令，便于复制调试。
     */
    private void printCurlCommand(CreateResponsesRequest request, Map<String, String> customHeaders) {
        try {
            String bodyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            String url = baseUrl + "/responses";

            StringBuilder curl = new StringBuilder();
            curl.append("curl --location '").append(url).append("' \\\n");
            curl.append("--header 'Content-Type: application/json' \\\n");
            curl.append("--header 'Authorization: Bearer ").append(apiKey).append("' \\\n");

            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    // 跳过 SDK 已自动处理的 header
                    if ("Content-Type".equalsIgnoreCase(entry.getKey())
                            || "Authorization".equalsIgnoreCase(entry.getKey())) {
                        continue;
                    }
                    curl.append("--header '").append(entry.getKey()).append(": ")
                            .append(entry.getValue()).append("' \\\n");
                }
            }

            curl.append("--data '").append(bodyJson).append("'");

            log.info("=== Responses API curl 命令 ===\n{}", curl);
        } catch (Exception e) {
            log.warn("打印 curl 命令失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int maxLen) {
        return s == null ? "null"
                : s.length() <= maxLen ? s
                : s.substring(0, maxLen) + "...(truncated)";
    }
}
