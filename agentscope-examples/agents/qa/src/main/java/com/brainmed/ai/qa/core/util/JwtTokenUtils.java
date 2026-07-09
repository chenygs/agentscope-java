package com.brainmed.ai.qa.core.util;

public class JwtTokenUtils {

    /**
     * 解析当前用户 ID。与 {@code QaChatController} 一致，当前固定返回 chenygs，
     * 后续接入 sa-token 后从登录态解析。
     */
    public static String resolveUserId() {
        return "chenygsv2";
    }
}
