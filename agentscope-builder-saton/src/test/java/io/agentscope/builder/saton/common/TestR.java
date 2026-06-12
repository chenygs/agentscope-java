package io.agentscope.builder.saton.common;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.common.json.JsonUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.core.type.TypeReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared test helpers for working with the {@link R} response wrapper.
 *
 * <p>Uses two-step deserialization (raw JSON → Jackson) instead of Spring's
 * {@code ParameterizedTypeReference} to avoid Jackson 3 generic-record
 * type-erasure issues when deserializing {@code R<T>}.
 */
public final class TestR {

    /** Login as admin and return the satoken. */
    public static String login(WebTestClient client) {
        String json = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        assertNotNull(json, "login response body is null");
        R<LoginResponse> r = parseR(json, new TypeReference<R<LoginResponse>>() {});
        assertNotNull(r.data(), "login R.data is null");
        return r.data().token();
    }

    /**
     * Extract {@code .data} from a {@code R<T>} response body.
     * Uses raw-JSON extraction + Jackson direct parse to preserve generic types.
     */
    public static <T> T data(WebTestClient.ResponseSpec spec,
                             TypeReference<R<T>> type) {
        String json = spec.expectBody(String.class).returnResult().getResponseBody();
        assertNotNull(json, "R response body is null");
        R<T> r = parseR(json, type);
        assertEquals(200, r.code(), "R.code should be 200, msg=" + r.msg());
        return r.data();
    }

    private static <T> R<T> parseR(String json, TypeReference<R<T>> type) {
        try {
            return JsonUtil.mapper().readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("failed to parse R response: " + json, e);
        }
    }

    private TestR() {}
}
