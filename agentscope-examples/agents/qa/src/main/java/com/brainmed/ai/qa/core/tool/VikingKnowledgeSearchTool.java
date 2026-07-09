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
 * 知识库检索工具（Viking search_knowledge 版）。
 *
 * <p>与 {@link KnowledgeSearchTool}（基于 Responses API 内置 knowledge_search，
 * 平台用 doubao 生成摘要）并存：本工具委托 {@link VikingKnowledgeSearcher} 直接检索
 * 知识库原始切片返回，不经模型摘要，主模型基于一手切片内容组织回答。
 *
 * <p>引用来源从 doc_info 提取后累加到 {@link RuntimeContext}（key=qa.citations），
 * 走现有 CITATIONS Custom 事件机制，前端展示与 web_search / knowledge_search 一致。
 */
@Slf4j
public class VikingKnowledgeSearchTool {

    private final VikingKnowledgeSearcher searcher;

    public VikingKnowledgeSearchTool(VikingKnowledgeSearcher searcher) {
        this.searcher = searcher;
    }

    @Tool(
            name = "knowledge_search_v2",
            description = """
                    在私域知识库中检索文档切片，返回命中的原始文档片段及其来源（非模型摘要）。
                    适用于需要精确引用知识库原文、专业领域知识、内部文档资料的场景。
                    返回多个文档切片内容，请基于切片内容组织回答，并用角标标注引用来源。
                    """,
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> knowledgeSearchV2(
            @ToolParam(name = "query", description = "检索关键词，应简洁准确地描述要查找的专业知识") String query,
            RuntimeContext ctx) {

        log.info("knowledge_search_v2 called, query='{}'", query);

        return searcher.search(query)
                .doOnNext(result -> {
                    List<SearchResult.Citation> citations = result.getCitations();
                    if (citations != null && !citations.isEmpty()) {
                        accumulateCitations(ctx, citations);
                        log.info("knowledge_search_v2: accumulated {} citations", citations.size());
                    }
                })
                .map(SearchResult::getText)
                .onErrorResume(e -> {
                    log.error("knowledge_search_v2 failed, query='{}': {}", query, e.getMessage(), e);
                    return Mono.just("知识库检索失败：" + e.getMessage() + "，请直接基于已有知识回答。");
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
