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

/**
 * Mem0 长期记忆提供者：直接调用 Mem0 REST API，不依赖 AgentScope SDK 封装。
 *
 * <p>绕过 {@code Mem0LongTermMemory} 的 {@code onErrorReturn("")} 静默吞错问题，
 * 所有 HTTP 错误均有完整日志，便于排查。
 *
 * <p>兼容 Mem0 开源云端（api.mem0.ai）和火山引擎托管 Mem0（连接地址:8000），
 * 统一使用 {@code Authorization: Token <key>} 认证。
 */
public class Mem0MemoryProvider implements LongTermMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(Mem0MemoryProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SEARCH_PATH = "/v1/memories/search/";
    private static final String ADD_PATH = "/v1/memories/";

//    private final String apiKey;
    private final WebClient webClient;

    public Mem0MemoryProvider(String apiKey, String apiBaseUrl) {
//        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Token " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[Mem0] 初始化: apiBaseUrl={}", apiBaseUrl);
    }

    @Override
    public Mono<String> search(Msg userQuery, String userId, String agentId) {
        String query = userQuery.getTextContent();
        if (query == null || query.isBlank()) {
            return Mono.just("");
        }

        JSONObject body = new JSONObject()
                .set("query", query)
                .set("user_id", userId);
        if (agentId != null && !agentId.isBlank()) {
            body.set("agent_id", agentId);
        }

        log.info("[Mem0] search request: userId={}, agentId={}, query={}", userId, agentId, query);

        return webClient.post()
                .uri(SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    return response.bodyToMono(String.class)
                            .doOnNext(resp -> log.debug("[Mem0] search response status={}, body={}",
                                    status, resp.length() > 200 ? resp.substring(0, 200) : resp))
                            .map(respBody -> {
                                if (status >= 400) {
                                    log.warn("[Mem0] search HTTP error: status={}, body={}", status, respBody);
                                    return "";
                                }
                                return parseSearchResponse(respBody);
                            });
                })
                .onErrorResume(e -> {
                    log.warn("[Mem0] search exception: userId={}, error={}", userId, e.getMessage());
                    return Mono.just("");
                })
                .defaultIfEmpty("");
    }

    @Override
    public Mono<Void> record(String userId, List<Msg> userMsgs, String assistantText, String agentId) {
        // 助手回复为空时不记录（无实质内容的对话不应进入长期记忆）
        if (assistantText == null || assistantText.isBlank()) {
            log.info("[Mem0] record skipped: no assistant response for userId={}", userId);
            return Mono.empty();
        }

        JSONArray messages = new JSONArray();
        for (Msg msg : userMsgs) {
            String text = msg.getTextContent();
            if (text != null && !text.isBlank()) {
                messages.add(new JSONObject()
                        .set("role", "user")
                        .set("content", text));
            }
        }
        // 追加 assistant 回复，让 Mem0 抽取引擎有完整上下文判断价值
        messages.add(new JSONObject()
                .set("role", "assistant")
                .set("content", assistantText));

        JSONObject body = new JSONObject()
                .set("messages", messages)
                .set("user_id", userId);
        if (agentId != null && !agentId.isBlank()) {
            body.set("agent_id", agentId);
        }

        log.info("[Mem0] record request: userId={}, agentId={}, userMsgs={}, assistantLen={}",
                userId, agentId, messages.size() - 1, assistantText.length());

        return webClient.post()
                .uri(ADD_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    return response.bodyToMono(String.class)
                            .doOnNext(resp -> log.info("[Mem0] record response: status={}, body={}",
                                    status, resp.length() > 200 ? resp.substring(0, 200) : resp));
                })
                .then();
    }

    @Override
    public String name() {
        return "mem0";
    }

    // ───────────────── 响应解析 ─────────────────

    /**
     * 解析 Mem0 search 响应。
     * 响应格式：{@code {"results": [{"memory": "...", "score": 0.8, ...}, ...]}}
     */
    private String parseSearchResponse(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode item : results) {
                String memory = item.path("memory").asText("");
                if (memory.isBlank()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(memory);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[Mem0] search parse failed: {}", e.getMessage(), e);
            return "";
        }
    }
}
