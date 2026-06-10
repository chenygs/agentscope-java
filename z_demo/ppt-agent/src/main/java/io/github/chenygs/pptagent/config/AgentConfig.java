package io.github.chenygs.pptagent.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import io.github.chenygs.pptagent.chat.middleware.ChatPersistenceMiddleware;
import io.github.chenygs.pptagent.chat.middleware.SessionAwareAgentProxy;
import io.github.chenygs.pptagent.chat.service.ChatSessionService;
import io.github.chenygs.pptagent.session.PptSessionStateStore;
import io.github.chenygs.pptagent.config.props.ImageSearchProperties;
import io.github.chenygs.pptagent.config.props.WebSearchProperties;
import io.github.chenygs.pptagent.tool.BaseTools;
import io.github.chenygs.pptagent.tool.ImageSearchTool;
import io.github.chenygs.pptagent.tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({WebSearchProperties.class, ImageSearchProperties.class})
@Slf4j
public class AgentConfig {

    @Bean
    public Toolkit toolkit(BaseTools baseTools, WebSearchTool webSearchTool,
                           ImageSearchTool imageSearchTool) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(baseTools);
        toolkit.registerTool(webSearchTool);
        toolkit.registerTool(imageSearchTool);

        // 连接 Sequential Thinking MCP 服务器
        McpClientWrapper mcpClient = McpClientBuilder.create("sequential-thinking").stdioTransport("npx.cmd", "-y", "@modelcontextprotocol/server-sequential-thinking").buildAsync().block();
        toolkit.registerMcpClient(mcpClient).block();

        return toolkit;
    }

    @Bean
    public ReActAgent agent(Toolkit toolkit) {
        return ReActAgent.builder().name("pptAgent").sysPrompt("""
                你是一个 PPT 设计助手。创作 PPT 时请遵循 skills/ppt-creator/SKILL.md 的工作流。
                先查 ppt-design-kb/ 选择配色/字体/图标，再规划大纲并生成 PPT。
                """).model(OpenAIChatModel.builder().apiKey(System.getProperty("DEEPSEEK_API_KEY")).baseUrl(System.getProperty("DEEPSEEK_BASE_URL")).modelName(System.getProperty("DEEPSEEK_DEFAULT_MODEL")).formatter(new DeepSeekFormatter()).stream(true).build()).toolkit(toolkit).build();
    }

    @Bean
    public HarnessAgent harnessAgent(Toolkit toolkit,
                                     ChatSessionService chatSessionService,
                                     Optional<AgentStateStore> sessionStateStore) throws IOException {
        Path workspace = Paths.get("./.agentscope/workspace");

        var builder = HarnessAgent.builder().name("pptAgent").sysPrompt("""
                        你是一个 PPT 设计助手。你可以使用以下能力：
                        1. web_search - 搜索互联网获取最新信息
                        2. search_image - 搜索配图（输入英文关键词，返回免费图片 URL）
                        3. sequential_thinking - 复杂问题分步推理
                        
                        创作 PPT 时必须遵循 skills/ppt-creator/SKILL.md 的工作流和输出格式。
                        模板库在 ppt-design-kb/ 目录下，包含 19 套专业模板。
                        """)
                .workspace(workspace)
                .enablePlanMode()
                .enableAgentTracingLog(true)
                .compaction(CompactionConfig
                        .builder()
                        .triggerMessages(50)
                        .keepMessages(20)
                        .flushBeforeCompact(true)
                        .offloadBeforeCompact(true)
                        .build()
                )
                .model(OpenAIChatModel
                        .builder()
                        .apiKey(System.getProperty("DEEPSEEK_API_KEY"))
                        .baseUrl(System.getProperty("DEEPSEEK_BASE_URL"))
                        .modelName(System.getProperty("DEEPSEEK_DEFAULT_MODEL"))
                        .formatter(new DeepSeekFormatter())
                        .stream(true)
                        .build()
                ).toolkit(toolkit)
//                .enablePendingToolRecovery(true)
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build());

        // 注入 AgentStateStore — 落 ppt_session 表，参照官方 MysqlAgentStateStore 的存储约定
        sessionStateStore.ifPresent(builder::stateStore);

        log.info("PptSessionStateStore injected into HarnessAgent");

        // 注册 ChatPersistenceMiddleware — 自动持久化对话消息到 chat_session_message 表
        builder.middleware(new ChatPersistenceMiddleware(chatSessionService));
        log.info("ChatPersistenceMiddleware registered");

        return builder.build();
    }

    @Bean
    public AguiAgentRegistryCustomizer aguiRegistryCustomizer(HarnessAgent harnessAgent) {
        return registry -> {
            // 注册 JDK 代理，拦截 stream() 注入 RuntimeContext
            Agent proxied = SessionAwareAgentProxy.create(harnessAgent);
            registry.register("default", proxied);
            log.info("SessionAwareAgentProxy registered");
        };
    }

    @Bean
    @ConditionalOnProperty(name = "agentscope.studio.enabled", havingValue = "true")
    public StudioMessageHook studioMessageHook() {
        if (!StudioManager.isInitialized()) {
            StudioManager.init().studioUrl("http://localhost:3000").project("default").initialize().block();
        }
        log.info("StudioMessageHook injected");
        return new StudioMessageHook(StudioManager.getClient());
    }
}
