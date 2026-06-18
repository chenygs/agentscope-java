package io.agentscope.builder.saton.common;

import java.util.List;
import java.util.Set;
import io.agentscope.builder.saton.auth.service.ex.AuthExceptionHandler;

/**
 * Unified API response wrapper.
 *
 * <p>{@code code=0} means success; non-zero means error.
 * Convention: error codes reuse HTTP status values (400, 401, 403, 404, 409 …)
 * for consistency with {@code AuthExceptionHandler}.
 *
 * @param <T> payload type; {@code Void} for no-data responses
 */
public record R<T>(int code, T data, String msg) {

    private static final int SUCCESS = 200;

    // ---- success ----

    public static <T> R<T> ok() {
        return new R<>(SUCCESS, null, "ok");
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, data, "ok");
    }

    public static <T> R<List<T>> okList(List<T> list) {
        return new R<>(SUCCESS, list, "ok");
    }

    public static <T> R<Set<T>> okSet(Set<T> set) {
        return new R<>(SUCCESS, set, "ok");
    }

    public static <T> R<PageData<T>> okPage(List<T> list, long total, int page, int size) {
        return new R<>(SUCCESS, new PageData<>(list, total, page, size), "ok");
    }

    // ---- error ----

    public static <T> R<T> fail(String msg) {
        return new R<>(500, null, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, null, msg);
    }
}
