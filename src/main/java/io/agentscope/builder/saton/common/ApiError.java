package io.agentscope.builder.saton.common;

public record ApiError(int code, String message) {

    public static ApiError of(int code, String message) {
        return new ApiError(code, message);
    }
}
