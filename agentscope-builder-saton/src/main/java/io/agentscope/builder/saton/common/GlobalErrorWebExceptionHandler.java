package io.agentscope.builder.saton.common;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Catches exceptions thrown by reactive WebFilters (notably sa-token's
 * {@code NotLoginException}, raised by {@code SaReactorFilter} before any
 * controller is reached). {@code @RestControllerAdvice} cannot see these.
 */
@Slf4j
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        NotLoginException notLogin = findNotLogin(ex);
        if (notLogin != null) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED,
                    R.fail(401, "not logged in: " + notLogin.getType()));
        }
        // Let other handlers (incl. @RestControllerAdvice) deal with the rest.
        return Mono.error(ex);
    }

    private static NotLoginException findNotLogin(Throwable t) {
        Throwable cur = t;
        for (int i = 0; cur != null && i < 8; i++) {
            if (cur instanceof NotLoginException nl) {
                return nl;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, R<?> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buf));
        } catch (Exception e) {
            log.warn("failed to serialize R error response", e);
            byte[] fallback = "{\"code\":401,\"data\":null,\"msg\":\"not logged in\"}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buf = exchange.getResponse().bufferFactory().wrap(fallback);
            return exchange.getResponse().writeWith(Mono.just(buf));
        }
    }
}
