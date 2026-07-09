package com.brainmed.ai.qa.orm.vo;

/**
 * 分享结果：返回 token 供前端构造分享链接。
 */
public record ShareInfoVo(
        String token,
        String url) {
}
