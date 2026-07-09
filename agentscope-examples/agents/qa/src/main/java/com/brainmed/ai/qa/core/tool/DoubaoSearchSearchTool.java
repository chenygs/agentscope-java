package com.brainmed.ai.qa.core.tool;

import com.brainmed.ai.qa.core.middleware.QaMessagePersistenceMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网搜索工具（豆包搜索 Custom 版）。
 *
 * <p>与 {@link WebSearchTool}（基于 Responses API 内置 web_search，平台用 doubao 生成摘要）并存：
 * 本工具委托 {@link DoubaoSearchSearcher} 直接获取 web 搜索结果（标题/摘要/链接），
 * 不经模型二次摘要，主模型基于一手搜索结果组织回答。
 *
 * <p>引用来源从 WebResults 的 Title/Url 提取后累加到 {@link RuntimeContext}（key=qa.citations），
 * 走现有 CITATIONS Custom 事件机制，前端展示与 web_search / knowledge_search_v2 一致。
 */
@Slf4j
public class DoubaoSearchSearchTool {

    private final DoubaoSearchSearcher searcher;

    public DoubaoSearchSearchTool(DoubaoSearchSearcher searcher) {
        this.searcher = searcher;
    }

    @Tool(
            name = "web_search_v2",
            description = """
                    联网搜索，获取实时互联网信息（返回搜索结果的标题、摘要与来源链接，非模型摘要）。
                    适用于需要实时资讯、最新进展、公开信息检索的场景。
                    返回多条搜索结果摘要，请基于摘要内容组织回答，并用角标标注引用来源。
                    """,
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> webSearchV2(
            @ToolParam(name = "query", description = "搜索关键词，应简洁准确描述要查找的信息（1~100字）") String query,
            @ToolParam(name = "time_range", description = """
                    搜索结果的时间范围，可选枚举值：OneDay（1天内）/ OneWeek（1周内）/ OneMonth（1月内）/ OneYear（1年内）/ YYYY-MM-DD..YYYY-MM-DD（指定日期区间）。
                    当用户提问包含明确的时间范围（如"最新""最近一周""本月""今年""2024年以来"等）时，填入对应枚举值；
                    无明确时间限定时传空字符串 ""，表示不限制时间。
                    """) String timeRange,
            RuntimeContext ctx) {

        log.info("web_search_v2 called, query='{}', time_range='{}'", query, timeRange);

        return searcher.search(query, timeRange)
                .doOnNext(result -> {
                    List<SearchResult.Citation> citations = result.getCitations();
                    if (citations != null && !citations.isEmpty()) {
                        accumulateCitations(ctx, citations);
                        log.info("web_search_v2: accumulated {} citations", citations.size());
                    }
                })
                .map(SearchResult::getText)
                .onErrorResume(e -> {
                    log.error("web_search_v2 failed, query='{}': {}", query, e.getMessage(), e);
                    return Mono.just("联网搜索失败：" + e.getMessage() + "，请直接基于已有知识回答。");
                });
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
