package com.brainmed.ai.qa.core.memory;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 基于火山引擎 Viking 记忆库的长期记忆提供者。
 *
 * <p>通过 VikingDB REST API 写入对话（后端自动抽取事件和画像），通过语义检索召回事件记忆。
 * 使用 Bearer Token 认证（与 Viking 知识库共用 API Key 和 host）。
 *
 * <p>定位记忆库使用 {@code collection_name} + {@code project_name}（控制台可见）。
 */
public class VikingMemoryProvider implements LongTermMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(VikingMemoryProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ADD_SESSION_PATH = "/api/memory/session/add";
    private static final String SEARCH_EVENT_PATH = "/api/memory/event/search";
    private static final String DEFAULT_ASSISTANT_ID = "brainmed-qa-assistant";

    private final String collectionName;
    private final String projectName;
    private final int searchLimit;
    private final WebClient webClient;

    /**
     * @param baseUrl        Viking 服务地址
     * @param apiKey         Bearer Token
     * @param collectionName 记忆库名称（控制台创建时填写）
     * @param projectName    项目名称（默认 "default"）
     * @param searchLimit    检索返回条数
     */
    public VikingMemoryProvider(String baseUrl, String apiKey, String collectionName,
                                String projectName, int searchLimit) {
        this.collectionName = collectionName;
        this.projectName = projectName;
        this.searchLimit = searchLimit;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[VikingMemory] 初始化: baseUrl={}, collection={}, project={}, limit={}",
                baseUrl, collectionName, projectName, searchLimit);
    }

    // ───────────────── LongTermMemoryProvider ─────────────────

    @Override
    public Mono<String> search(Msg userQuery, String userId, String agentId) {
        String query = userQuery.getTextContent();
        if (query == null || query.isBlank()) {
            return Mono.just("");
        }

        JSONObject filter = new JSONObject()
                .set("user_id", userId)
                .set("memory_type", new String[]{"event_v1"});
        if (agentId != null && !agentId.isBlank()) {
            filter.set("assistant_id", agentId);
        }

        JSONObject body = new JSONObject()
                .set("collection_name", collectionName)
                .set("project_name", projectName)
                .set("query", query)
                .set("filter", filter)
                .set("limit", searchLimit);

        log.info("[VikingMemory] search request: userId={}, agentId={}, query={}", userId, agentId, query);

        return webClient.post()
                .uri(SEARCH_EVENT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .doOnNext(resp -> log.debug("[VikingMemory] search response status={}", response.statusCode())))
                .map(this::parseSearchResponse)
                .defaultIfEmpty("");
    }

    @Override
    public Mono<Void> record(String userId, List<Msg> userMsgs, String assistantText, String agentId) {
        // 助手回复为空时不记录
        if (assistantText == null || assistantText.isBlank()) {
            log.info("[VikingMemory] record skipped: no assistant response for userId={}", userId);
            return Mono.empty();
        }

        // 拼接所有 user 消息文本
        String userContent = userMsgs.stream()
                .map(Msg::getTextContent)
                .reduce("", (a, b) -> a + "\n" + b).trim();
        if (userContent.isBlank()) {
            return Mono.empty();
        }

        return addSession(userContent, assistantText, userId, agentId)
                .then();
    }

    @Override
    public String name() {
        return "viking";
    }

    // ───────────────── Viking API 调用 ─────────────────

    /**
     * 向记忆库写入一轮对话（user + assistant），Viking 后端自动抽取事件和画像。
     */
    private Mono<String> addSession(String userContent, String assistantContent, String userId, String agentId) {
        String sessionId = UUID.randomUUID().toString();
        String assistantId = (agentId != null && !agentId.isBlank()) ? agentId : DEFAULT_ASSISTANT_ID;

        JSONArray messages = new JSONArray();
        if (!userContent.isBlank()) {
            messages.add(new JSONObject()
                    .set("role", "user")
                    .set("content", userContent));
        }
        if (!assistantContent.isBlank()) {
            messages.add(new JSONObject()
                    .set("role", "assistant")
                    .set("content", assistantContent));
        }

        JSONObject body = new JSONObject()
                .set("collection_name", collectionName)
                .set("project_name", projectName)
                .set("session_id", sessionId)
                .set("messages", messages)
                .set("metadata", new JSONObject()
                        .set("default_user_id", userId)
                        .set("default_assistant_id", assistantId)
                        .set("time", System.currentTimeMillis()));

        log.info("[VikingMemory] addSession request: userId={}, agentId={}, sessionId={}, userLen={}, assistantLen={}",
                userId, assistantId, sessionId, userContent.length(), assistantContent.length());

        return webClient.post()
                .uri(ADD_SESSION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .doOnNext(resp -> log.info("[VikingMemory] addSession response status={}: {}",
                                response.statusCode(), resp)));
    }

    // ───────────────── 响应解析 ─────────────────

    /**
     * 解析 search_event_memory 响应，把事件记忆列表拼成一段文本。
     * 响应结构：{@code data.result_list[].memory_info}
     */
    private String parseSearchResponse(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("unknown error");
                log.warn("[VikingMemory] search non-zero code: code={}, message={}", code, msg);
                return "";
            }

            JsonNode result_list = root.path("data").path("result_list");
            if (!result_list.isArray() || result_list.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode item : result_list) {
                JsonNode memoryInfo = item.path("memory_info");
                String content = "";
                if (memoryInfo.isObject()) {
                    content = memoryInfo.path("content").asText("");
                    if (content.isEmpty()) {
                        content = memoryInfo.path("summary").asText("");
                    }
                    if (content.isEmpty()) {
                        content = memoryInfo.path("description").asText("");
                    }
                    if (content.isEmpty()) {
                        content = memoryInfo.toString();
                    }
                } else if (memoryInfo.isTextual()) {
                    content = memoryInfo.asText("");
                }

                if (content.isEmpty()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(content);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[VikingMemory] search parse failed: {}", e.getMessage(), e);
            return "";
        }
    }
}
