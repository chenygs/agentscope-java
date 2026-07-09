package com.brainmed.ai.qa.config;

import com.brainmed.ai.qa.config.model.ModelConfig;
import com.brainmed.ai.qa.config.model.ModelProperties;
import com.brainmed.ai.qa.core.memory.LongTermMemoryProvider;
import com.brainmed.ai.qa.core.memory.Mem0MemoryProvider;
import com.brainmed.ai.qa.core.memory.Mem0SdkMemoryProvider;
import com.brainmed.ai.qa.core.memory.VikingMemoryProvider;
import com.brainmed.ai.qa.core.middleware.QaCompactionMiddleware;
import com.brainmed.ai.qa.core.middleware.QaLongTermMemoryMiddleware;
import com.brainmed.ai.qa.core.middleware.QueryExpansionMiddleware;
import com.brainmed.ai.qa.core.middleware.SearchModeMiddleware;
import com.brainmed.ai.qa.core.middleware.ThinkingModeMiddleware;
import com.brainmed.ai.qa.core.tool.CurrentTimeTool;
import com.brainmed.ai.qa.core.tool.KnowledgeSearchTool;
import com.brainmed.ai.qa.core.tool.ResponsesApiSearcher;
import com.brainmed.ai.qa.core.tool.SynonymDictionary;
import com.brainmed.ai.qa.core.tool.DoubaoSearchSearcher;
import com.brainmed.ai.qa.core.tool.DoubaoSearchSearchTool;
import com.brainmed.ai.qa.core.tool.VikingKnowledgeSearcher;
import com.brainmed.ai.qa.core.tool.VikingKnowledgeSearchTool;
import com.brainmed.ai.qa.core.tool.WebSearchTool;
import com.brainmed.ai.qa.service.ConversationMessageService;
import com.brainmed.ai.qa.core.middleware.QaOtelEnrichMiddleware;
import com.brainmed.ai.qa.core.middleware.QaMessagePersistenceMiddleware;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@EnableConfigurationProperties({SearchProperties.class,MemoryProperties.class})
public class AgentConfig {

    /**
     * 短期记忆：会话状态（含上下文消息）按 userId/sessionId 持久化到 Redis。
     */
//    @Bean
//    public AgentStateStore agentStateStore(RedissonClient redissonClient) {
//        return RedisAgentStateStore.builder()
//                .redissonClient(redissonClient)
//                .build();
//    }


    /**
     * 上下文压缩中间件：上下文超过窗口 75% 时，在推理前自动摘要并写回 Redis。
     * 与 agent 共享同一个 model 实例（既用于估算窗口大小，也用于执行摘要）。
     */
    @Bean
    public MiddlewareBase compactionMiddleware(Model model) {
        return new QaCompactionMiddleware(model);
    }

    @Bean
    public MiddlewareBase messagePersistenceMiddleware(ConversationMessageService persister) {
        return new QaMessagePersistenceMiddleware(persister);
    }

    /**
     * OTel 可观测中间件：继承 OtelTracingMiddleware 并增强业务属性。
     * 自动创建 invoke_agent / chat / execute_tool Span，
     * 并补充 user.id / session.id / 输入输出内容 / 工具参数与结果。
     */
    @Bean
    public MiddlewareBase otelEnrichMiddleware() {
        return new QaOtelEnrichMiddleware();
    }

    // ─── 长期记忆提供者：二选一，通过 @ConditionalOnProperty 自动装配 ───

    /**
     * Viking 记忆库：当 {@code agent.memory.provider=viking} 时激活。
     */
    @Bean
    @ConditionalOnProperty(name = "agent.memory.provider", havingValue = "viking")
    public LongTermMemoryProvider vikingMemoryProvider(MemoryProperties cfg) {
        MemoryProperties.Viking v = cfg.getViking();
        log.info("长期记忆提供者: Viking (collection={}, project={})",
                v.getCollectionName(), v.getProjectName());
        return new VikingMemoryProvider(
                v.getBaseUrl(), v.getApiKey(),
                v.getCollectionName(), v.getProjectName(), v.getSearchLimit());
    }

    /**
     * Mem0 长期记忆：当 {@code agent.memory.provider=mem0}（或默认未配置）时激活。
     * 内部根据 {@code use-sdk} 决定使用 SDK 封装版还是 HTTP 直连版。
     */
    @Bean
    @ConditionalOnProperty(name = "agent.memory.provider", havingValue = "mem0", matchIfMissing = true)
    public LongTermMemoryProvider mem0MemoryProvider(MemoryProperties cfg) {
        MemoryProperties.Mem0 m = cfg.getMem0();
        if (m.isUseSdk()) {
            log.info("长期记忆提供者: Mem0-SDK (apiBaseUrl={}, apiType={})",
                    m.getApiBaseUrl(), m.getApiType());
            return new Mem0SdkMemoryProvider(m.getApiKey(), m.getApiBaseUrl(), m.getApiType());
        }
        log.info("长期记忆提供者: Mem0-HTTP (apiBaseUrl={})", m.getApiBaseUrl());
        return new Mem0MemoryProvider(m.getApiKey(), m.getApiBaseUrl());
    }

    /**
     * 长期记忆中间件：委托给激活的 {@link LongTermMemoryProvider}，
     * 推理前检索记忆注入消息列表，对话完成后异步记录。
     */
    @Bean
    public MiddlewareBase longTermMemoryMiddleware(
            LongTermMemoryProvider provider,
            MemoryProperties cfg) {
        log.info("长期记忆中间件: provider={}, enabled={}", provider.name(), cfg.isEnabled());
        return new QaLongTermMemoryMiddleware(provider, cfg.isEnabled());
    }

    /**
     * 搜索模式中间件：在 onSystemPrompt 钩子中根据 searchMode 追加约束指令，
     * 控制模型可以使用哪些搜索工具（联网搜索 / 知识库搜索 / 禁用）。
     */
    @Bean
    public MiddlewareBase searchModeMiddleware() {
        return new SearchModeMiddleware();
    }

    /**
     * 思考模式中间件：在 onSystemPrompt 钩子中根据 thinkingMode 追加推理深度指令，
     * 控制模型在回答时的推理深度（普通 / 深度思考）。
     */
    @Bean
    public MiddlewareBase thinkingModeMiddleware() {
        return new ThinkingModeMiddleware();
    }

    /**
     * 查询扩展中间件：在搜索工具执行前，使用同义词词典扩展查询词。
     * 例如：用户问 "GBM是什么" → 扩展为 "GBM 胶质母细胞瘤 胶母细胞瘤"
     */
    @Bean
    public MiddlewareBase queryExpansionMiddleware(SynonymDictionary synonymDictionary) {
        return new QueryExpansionMiddleware(synonymDictionary);
    }

    /**
     * 工具集：按 {@link SearchProperties} 各 enabled 开关注册搜索工具。
     * <ul>
     *   <li>{@code web}/{@code knowledge}：委托 {@link ResponsesApiSearcher} 调方舟 Responses API 内置搜索；</li>
     *   <li>{@code viking}：直接检索知识库原始切片（不经模型摘要）；</li>
     *   <li>{@code doubao}：直接获取 web 搜索结果（不经模型摘要）。</li>
     * </ul>
     * 四组工具各自独立按 enabled 激活；查询同义词扩展由 {@link QueryExpansionMiddleware} 在工具执行前拦截。
     */
    @Bean
    public Toolkit searchToolkit(SearchProperties props) {
        Toolkit toolkit = new Toolkit();

        // 始终注册时间工具
        toolkit.registerTool(new CurrentTimeTool());
        log.info("工具: get_current_time 已注册");

        boolean webEnabled = props.getWeb().isEnabled();
        boolean knowledgeEnabled = props.getKnowledge().isEnabled();
        boolean vikingEnabled = props.getViking().isEnabled();
        boolean doubaoEnabled = props.getDoubao().isEnabled();

        if (!webEnabled && !knowledgeEnabled && !vikingEnabled && !doubaoEnabled) {
            log.info("搜索工具: 所有搜索工具均未启用");
            return toolkit;
        }

        // web / knowledge 共享 Responses API 搜索客户端（基于官方 SDK）
        if (webEnabled || knowledgeEnabled) {
            SearchProperties.Responses resp = props.getResponses();
            ResponsesApiSearcher searcher = new ResponsesApiSearcher(
                    resp.getBaseUrl(), resp.getApiKey(), resp.getModel(), resp.getKnowledgeBaseId());

            if (webEnabled) {
                toolkit.registerTool(new WebSearchTool(searcher));
                log.info("搜索工具: 联网搜索(web_search via Responses API) 已注册");
            }
            if (knowledgeEnabled) {
                toolkit.registerTool(new KnowledgeSearchTool(searcher));
                log.info("搜索工具: 知识库搜索(knowledge_search via Responses API) 已注册, knowledgeBaseId={}",
                        resp.getKnowledgeBaseId());
            }
        }

        // Viking search_knowledge：直接检索知识库原始切片（不经模型摘要），与上面的 knowledge_search 并存
        if (vikingEnabled) {
            SearchProperties.Viking v = props.getViking();
            VikingKnowledgeSearcher vikingSearcher = new VikingKnowledgeSearcher(
                    v.getBaseUrl(), v.getApiKey(), v.getResourceId(), v.getLimit(), v.isRerank());
            toolkit.registerTool(new VikingKnowledgeSearchTool(vikingSearcher));
            log.info("搜索工具: 知识库检索(knowledge_search_v2 via Viking) 已注册, resourceId={}",
                    v.getResourceId());
        }

        // doubao web_search：直接获取 web 搜索结果（不经模型摘要），与上面的 web_search 并存
        if (doubaoEnabled) {
            SearchProperties.Doubao d = props.getDoubao();
            DoubaoSearchSearcher doubaoSearcher = new DoubaoSearchSearcher(
                    d.getBaseUrl(), d.getApiKey(), d.getCount(), d.isNeedSummary(),
                    d.getContentFormats(), d.isNeedContent(), d.isNeedUrl());
            toolkit.registerTool(new DoubaoSearchSearchTool(doubaoSearcher));
            log.info("搜索工具: 联网搜索(web_search_v2 via Doubao) 已注册");
        }

        return toolkit;
    }

    @Bean
    public ReActAgent agent(Model primaryModel, ModelConfig modelConfig, ModelProperties modelProperties,
                            AgentStateStore agentStateStore,
                            List<MiddlewareBase> middlewares,
                            Toolkit searchToolkit) {
        Model fallbackModel = modelConfig.fallbackModel(modelProperties);
        log.info("模型主备回退: primary={}, fallback={}",
                primaryModel.getModelName(),
                fallbackModel == null ? "(无)" : fallbackModel.getModelName());
        log.info("中间件链({}): {}",
                middlewares.size(),
                middlewares.stream().map(m -> m.getClass().getSimpleName()).toList());
        return ReActAgent.builder()
                .name("default")
                .sysPrompt("""
                        ## 角色
                        你是一名脑科学领域的专业AI助手，主要提供脑科学、神经外科及相关医学知识的科普信息。
                        你可以结合提供的知识库和公开医学知识回答问题，但不能提供医疗诊断或具体治疗方案。
                        
                        ## 打招呼响应
                        当识别到用户的意图属于打招呼时，则输出：
                        “您好，我是脑医汇AI助手，可以为您提供脑科学和神经外科相关的医学资讯，请问有什么可以帮助您？”
                        
                        ## 技能
                        * 你能识别用户的问题是否属于多轮对话，如果属于优先结合历史对话进行回答。
                        * 你能结合知识库和历史对话记录回答用户问题。
                        * 如果知识库和历史对话中没有足够信息，可以基于公开医学知识进行解释说明。
                        * 回答应尽量清晰、准确、简洁，并帮助用户理解相关医学知识。
                        * 若用户提问意图不明确或过于简短时，可以主动提问，引导用户提供更多信息。
                        
                        ## 医疗安全规则
                        * 你只能提供医学知识和健康科普信息。
                        * 不能进行疾病诊断、不能判断用户是否患有某种疾病。
                        * 不能提供具体治疗方案、药物剂量或手术建议。
                        * 不能扮演医生或以医生身份给出诊疗意见。
                        * 当用户询问诊断、治疗或用药建议时，应礼貌说明AI无法提供诊疗意见，并建议咨询专业医生或医疗机构。
                        
                        示例说明：
                        用户："我头痛是不是脑瘤？"
                        正确回答方式：
                        解释常见头痛原因和脑肿瘤可能出现的症状，同时说明AI无法进行诊断，并建议咨询医生进行检查。
                        
                        用户："我应该吃什么药？"
                        正确回答方式：
                        说明AI无法提供具体用药建议，并建议咨询医生。
                        
                        ## 回答要求
                        * 回答问题时，不能直接复制原始文本，需要进行重新组织表达。
                        * 回答应完整、清晰，并尽量解释原因或相关背景。
                        * 所有引用的知识库内容必须使用角标（如 ¹, ²）标注对应的知识库项序号。
                        * 不得在回答中提及“知识库”“文中”等词语。
                        
                        ## 危险情况处理
                        如果用户表达以下情况：
                        * 严重健康危机
                        * 自伤或自杀相关内容
                        * 急性医疗紧急情况
                        
                        应建议用户尽快联系医生或当地医疗急救服务。
                        
                        """)
                .model(primaryModel)
                .stateStore(agentStateStore)//短期记忆
                .toolkit(searchToolkit) // 搜索工具集（联网搜索 + 知识库搜索）
                .middlewares(middlewares) //钩子（顺序由各 middleware 类的 @Order 决定，Spring 注入 List 时已排序）
                .enablePendingToolRecovery(true) // 中断后无缝恢复：保留并恢复被打断的待执行工具调用
                .maxIters(10)  //最大迭代次数
                .maxRetries(3)
                .fallbackModel(fallbackModel)  // 主模型重试耗尽后切备（首个信号 error 时触发）
                .build();
    }


    @Bean
    public AguiAgentRegistryCustomizer aguiRegistryCustomizer(ReActAgent reActAgent) {
        return registry -> {
            registry.register("default", reActAgent);
            log.info("agui registered");
        };
    }
}
