package io.agentscope.builder.saton.session.service;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 暴露进程级 {@link AgentStateStore}，由 ReActAgent / HarnessAgent 用作
 * {@code agent_state} 等状态的持久化后端。
 *
 * <p>后端走 Redis（通过 Redisson）。{@link RedissonClient} 由
 * {@code redisson-spring-boot-starter} 根据 {@code spring.data.redis.*} 自动装配。
 *
 * <p>Key 前缀使用 {@link RedisAgentStateStore} 默认值 {@code agentscope:session:}。
 */
@Configuration
public class SessionStoreConfig {

    @Bean
    public AgentStateStore agentStateStore(RedissonClient redisson) {
        return RedisAgentStateStore.builder()
                .redissonClient(redisson)
                .build();
    }
}
