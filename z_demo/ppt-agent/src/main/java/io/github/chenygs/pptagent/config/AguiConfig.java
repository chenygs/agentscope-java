package io.github.chenygs.pptagent.config;

import io.agentscope.core.agent.Agent;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.github.chenygs.pptagent.agent.middleware.ChatSessionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * AG-UI 相关 Bean 的自定义配置。
 *
 * <p>覆盖默认的 {@link ThreadSessionManager}，在每请求解析 agent 时设置
 * {@link ChatSessionContext}，使 SessionAwareAgentProxy 能获取当前 threadId
 * 并注入 RuntimeContext，最终 ChatPersistenceMiddleware 可从中读取 sessionId。
 */
@Configuration
public class AguiConfig {

    @Bean
    @ConditionalOnMissingBean
    public ThreadSessionManager threadSessionManager(AguiProperties props) {
        return new ThreadSessionManager(
                props.getMaxThreadSessions(), props.getSessionTimeoutMinutes()) {

            @Override
            public Agent getOrCreateAgent(String threadId, String agentId,
                                          Supplier<Agent> agentFactory) {
                // 在每请求的 agent 解析中设置 ThreadLocal，
                // 后续 SessionAwareAgentProxy 可从中读取 threadId。
                ChatSessionContext.setCurrentThreadId(threadId);
                return super.getOrCreateAgent(threadId, agentId, agentFactory);
            }
        };
    }
}
