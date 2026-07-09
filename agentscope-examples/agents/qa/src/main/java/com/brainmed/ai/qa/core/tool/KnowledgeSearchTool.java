package com.brainmed.ai.qa.core.tool;

import com.brainmed.ai.qa.core.middleware.QaMessagePersistenceMiddleware;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库搜索工具：委托 {@link ResponsesApiSearcher} 调用方舟 Responses API 内置的
 * {@code knowledge_search} 工具，平台自动处理知识库检索、结果排序和摘要生成。
 *
 * <p>模型在 ReAct 循环中按需调用此工具，获取企业私域知识库中的信息。
 * 搜索结果全文由 CopilotKit 工具调用 UI 的 RESULT 区域展示。
 */
@Slf4j
public class KnowledgeSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ResponsesApiSearcher searcher;

    public KnowledgeSearchTool(ResponsesApiSearcher searcher) {
        this.searcher = searcher;
    }

    @Tool(
            name = "knowledge_search",
            description = """
                    在私域知识库中搜索信息，适用于查询企业内部文档、产品手册、技术文档、行业资料等专有知识。
                    当用户的问题涉及专业领域知识、内部资料或需要引用权威文档时使用此工具。
                    返回搜索结果的纯文本摘要，请直接基于摘要内容回答用户问题。
                    """,
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> knowledgeSearch(
            @ToolParam(name = "query", description = "搜索关键词，应简洁准确地描述要查找的专业知识") String query,
            RuntimeContext ctx) {

        log.info("knowledge_search called, query='{}'", query);

        try {
            ResponseObject resp = searcher.knowledgeSearchRaw(query);
            SearchResult result = parseResponse(resp);

            // 从原始 annotation 节点中提取 doc_citation 引用
            List<SearchResult.Citation> citations = extractDocCitations(result.getRawAnnotations());
            if (!citations.isEmpty()) {
                accumulateCitations(ctx, citations);
                log.info("knowledge_search: accumulated {} citations", citations.size());
            }
            return Mono.just(result.getText());
        } catch (Exception e) {
            log.error("knowledge_search failed, query='{}': {}", query, e.getMessage(), e);
            return Mono.just("知识库搜索失败：" + e.getMessage() + "，请直接基于已有知识回答。");
        }
    }

    /**
     * 解析 Responses API 响应对象，提取正文文本和原始 annotation 节点。
     */
    private static SearchResult parseResponse(ResponseObject resp) {
        try {
            String json = MAPPER.writeValueAsString(resp);
            JsonNode root = MAPPER.readTree(json);
            JsonNode output = root.get("output");
            if (output == null || !output.isArray() || output.isEmpty()) {
                return new SearchResult("搜索未返回结果。", Collections.emptyList(), Collections.emptyList());
            }

            StringBuilder sb = new StringBuilder();
            List<JsonNode> annotationNodes = new ArrayList<>();

            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText(""))) continue;
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) continue;
                for (JsonNode block : content) {
                    if (!"output_text".equals(block.path("type").asText(""))) continue;
                    String text = block.path("text").asText("");
                    if (!text.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                    JsonNode annotations = block.get("annotations");
                    if (annotations != null && annotations.isArray()) {
                        annotations.forEach(annotationNodes::add);
                    }
                }
            }

            if (sb.isEmpty()) {
                return new SearchResult("搜索结果（原始）：\n" + json.substring(0, Math.min(json.length(), 3000)),
                        Collections.emptyList(), annotationNodes);
            }
            return new SearchResult(sb.toString().trim(), Collections.emptyList(), annotationNodes);
        } catch (Exception e) {
            return new SearchResult("搜索结果解析失败：" + e.getMessage(), Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 从原始 annotation 节点中提取 doc_citation 类型引用。
     * 知识库文档没有外部 URL，用 docName 做标题。
     */
    private static List<SearchResult.Citation> extractDocCitations(List<JsonNode> annotations) {
        List<SearchResult.Citation> citations = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (JsonNode ann : annotations) {
            if (!"doc_citation".equals(ann.path("type").asText(""))) continue;

            String docId = ann.path("doc_id").asText("");
            String docName = ann.path("doc_name").asText("");
            if (docName.isEmpty() && docId.isEmpty()) continue;
            if (!seen.add(docId.isEmpty() ? docName : docId)) continue;

            String displayTitle = !docName.isEmpty() ? docName : docId;
            citations.add(new SearchResult.Citation(citations.size() + 1, displayTitle, ""));
        }
        return citations;
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

    public static void main(String[] args) throws JsonProcessingException {
        String json = """
                {
                    "createdAt": 1782978182,
                    "id": "resp_0217829781811314087c9e55f8b322b82cd17f72450d06e3a345f",
                    "maxOutputTokens": 32768,
                    "model": "doubao-seed-1-6-250615",
                    "object": "response",
                    "output": [
                        {
                            "id": "rs_02178297818266000000000000000000000ffffac180bf2344988",
                            "summary": [
                                {
                                    "type": "summary_text",
                                    "text": "我现在需要回答用户的问题：“GBM是什么”。首先，我要确定GBM在这里可能指的是什么。GBM是一个缩写，可能有多个含义，比如在医学领域、金融领域或者其他学科中都有可能。根据用户提供的工具，只有brainmed知识库可用，所以我应该优先考虑医学相关的术语。\\n\\n在医学中，GBM最常见的可能是指胶质母细胞瘤（Glioblastoma Multiforme），这是一种恶性程度很高的脑肿瘤。不过，为了确认这一点，我需要使用knowledge_search工具来搜索brainmed知识库，输入“GBM是什么”作为查询。\\n\\n接下来，我需要按照要求的格式进行工具调用。用户提供的工具名称是knowledge_search，参数包括name和queries。name必须是brainmed，queries是一个数组，里面包含搜索问题。因此，正确的调用应该是：\\n\\n<|FunctionCallBegin|>[{\\"name\\":\\"knowledge_search\\",\\"parameters\\":{\\"name\\":\\"brainmed\\",\\"queries\\":[\\"GBM是什么\\"]}}]<|FunctionCallEnd|>\\n\\n这样就能从脑医汇知识库中获取关于GBM的定义和相关信息，确保回答的准确性。不需要并行调用其他工具，因为只有一个知识库可用，且问题明确指向医学领域。因此，单工具调用即可满足需求。"
                                }
                            ],
                            "status": "completed",
                            "type": "reasoning"
                        },
                        {
                            "queries": [
                                "GBM是什么"
                            ],
                            "knowledgeResourceId": "kb-3e74dd6ef65b05bf",
                            "status": "completed",
                            "id": "ks_02178297819021900000000000000000000ffffac180bf2a56c72",
                            "type": "knowledge_search_call"
                        },
                        {
                            "id": "rs_02178297819220000000000000000000000ffffac180bf2796d06",
                            "summary": [
                                {
                                    "type": "summary_text",
                                    "text": "我现在需要处理用户的问题：“请搜索以下内容并返回搜索结果：GBM是什么”。根据之前的对话历史，用户已经调用了knowledge_search工具，搜索知识库brainmed，查询“GBM是什么”，并且得到了三个搜索结果。现在我需要根据这些搜索结果来整理回答。\\n\\n首先，我需要仔细阅读每个搜索结果的内容，提取关键信息。知识库资料1主要讨论GBM的起源，指出它是胶质母细胞瘤，一种侵袭性极强的恶性脑肿瘤，中位生存期15个月，起源于室管膜下区的基因突变细胞，特别是星形胶质细胞样神经干细胞。资料2则综述了GBM的现代治疗方法，包括质子束治疗、肿瘤治疗电场、CAR-T细胞、溶瘤病毒、基因编辑等，但提到这些方法只能延长生存期几个月，且面临血脑屏障和成本问题。资料3介绍了GBM学院及其免疫治疗进展，提到免疫抑制微环境，联合治疗如帕博利珠单抗、TTFields和TMZ的三联疗法，以及相关的临床研究结果。\\n\\n接下来，我需要判断这些信息是否足够回答用户的问题“GBM是什么”。用户的问题比较基础，可能需要定义、类型、特征、预后等基本信息。资料1提供了GBM的全称（胶质母细胞瘤）、性质（恶性脑肿瘤）、侵袭性、生存期、起源细胞等关键信息。资料2和3虽然涉及治疗和免疫进展，但可能超出了“是什么”的范畴，但也可以作为补充信息，说明其治疗现状和挑战。\\n\\n不过，用户的问题核心是“GBM是什么”，所以重点应放在定义、分类、临床表现、预后等基础信息上。资料1已经涵盖了这些内容，包括全称、恶性程度、生存期、起源。可能还需要确认是否有其他重要信息，比如是否属于胶质瘤的一种，是否有其他别名，或者是否有不同的分型（如IDH野生型）。资料1中提到了56.3% IDH野生型GBM患者，这可能是一个分型的信息，可以纳入回答。\\n\\n是否需要进一步搜索？比如，是否GBM还有其他定义或重要特征未被覆盖？比如，是否属于WHO分级中的IV级？资料1中没有明确提到WHO分级，但根据常识，GBM通常是WHO IV级胶质瘤，但用户可能需要准确的信息。不过当前搜索结果中没有提到这一点，是否需要补充搜索？\\n\\n但根据现有的搜索结果，资料1已经明确说明GBM是“侵袭性极强和无法治愈的恶性脑肿瘤”，中位生存期15个月，起源于SVZ的突变细胞。可能已经足够回答“是什么”的问题。如果用户需要更详细的分级信息，可能需要进一步搜索，但用户的问题比较直接，可能不需要。此外，资料中提到了IDH野生型，这可能是一个重要的分型，应包含在回答中。\\n\\n因此，现有的信息已经足够回答用户的问题，不需要继续搜索。接下来需要将搜索结果中的关键信息整理成自然语言回答，确保准确、简洁，涵盖定义、性质、生存期、起源、分型等要点，并适当提及治疗现状作为补充。"
                                }
                            ],
                            "status": "completed",
                            "type": "reasoning"
                        },
                        {
                            "content": [
                                {
                                    "text": "胶质母细胞瘤（GBM）是一种侵袭性极强且无法治愈的恶性脑肿瘤，总体生存期中位数约为15个月。其驱动突变起源细胞被证实为人类室管膜下区（SVZ）中携带GBM驱动突变基因（如EGFR突变、TERT启动子突变等）的星形胶质细胞样神经干细胞，这些突变细胞可迁移至大脑远隔区域并导致肿瘤发生。在GBM患者中，56.3%的IDH野生型病例存在此类SVZ起源的低水平驱动突变。\\n\\n目前，GBM的治疗面临诸多挑战，现有疗法（如质子束治疗、肿瘤治疗电场、CAR-T细胞疗法、溶瘤病毒、CRISPR基因编辑等）仅能使患者中位生存期延长数月。免疫治疗单独应用收效有限，联合治疗（如帕博利珠单抗+肿瘤电场治疗+替莫唑胺）被探索以克服免疫抑制微环境，早期研究显示出较高的无进展生存期（PFS），但需更大规模临床试验验证。血脑屏障穿透、治疗成本及疗效局限性仍是当前治疗的主要障碍。",
                                    "annotations": [
                                        {
                                            "type": "doc_citation",
                                            "docId": "pgc_12861",
                                            "docName": "GBM起源于室管膜下区的基因突变细胞",
                                            "chunkId": 0
                                        },
                                        {
                                            "type": "doc_citation",
                                            "docId": "pgc_46305",
                                            "docName": "【综述】胶质母细胞瘤的彻底变革性的治疗:现代治疗方法的全面概述",
                                            "chunkId": 10
                                        },
                                        {
                                            "type": "doc_citation",
                                            "docId": "pgc_26397",
                                            "docName": "GBM学院新春献礼：脑胶质瘤免疫新进展（一）",
                                            "chunkId": 0
                                        }
                                    ],
                                    "type": "output_text"
                                }
                            ],
                            "status": "completed",
                            "id": "msg_02178297820992200000000000000000000ffffac180bf2377be4",
                            "role": "assistant",
                            "type": "message"
                        }
                    ],
                    "serviceTier": "default",
                    "status": "completed",
                    "tools": [
                        {
                            "knowledgeResourceId": "kb-3e74dd6ef65b05bf",
                            "limit": 30,
                            "type": "knowledge_search"
                        }
                    ],
                    "usage": {
                        "inputTokens": 3427,
                        "outputTokens": 1234,
                        "totalTokens": 4661,
                        "inputTokensDetails": {
                            "cachedTokens": 0
                        },
                        "outputTokensDetails": {
                            "reasoningTokens": 958
                        },
                        "toolUsage": {
                            "knowledge_search": 1
                        },
                        "toolUsageDetails": {
                            "knowledge_search": {
                                "kb-3e74dd6ef65b05bf": 1
                            }
                        }
                    },
                    "caching": {
                        "type": "disabled"
                    },
                    "store": true,
                    "expireAt": 1783237381
                }
                
                """;

        JsonNode root = MAPPER.readTree(json);
        JsonNode output = root.get("output");
        if (output == null || !output.isArray() || output.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        List<JsonNode> annotationNodes = new ArrayList<>();

        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText(""))) continue;
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) continue;
            for (JsonNode block : content) {
                if (!"output_text".equals(block.path("type").asText(""))) continue;
                String text = block.path("text").asText("");
                if (!text.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
                JsonNode annotations = block.get("annotations");
                if (annotations != null && annotations.isArray()) {
                    annotations.forEach(annotationNodes::add);
                }
            }
        }

        if (sb.isEmpty()) {
            return;
        }
    }
}
