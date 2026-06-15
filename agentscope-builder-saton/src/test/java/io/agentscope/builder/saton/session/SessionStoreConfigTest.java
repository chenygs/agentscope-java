package io.agentscope.builder.saton.session;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试 {@link SessionStoreConfig}，断言它暴露的是 Redis-backed {@link AgentStateStore}。
 *
 * <p>不走 Spring context，避免连真实 Redis；RedissonClient 用 mock 替身（{@link RedisAgentStateStore}
 * 的 builder 只在 build 时持有 client 引用，不会立即发起连接）。
 */
class SessionStoreConfigTest {

    @Test
    void exposesRedisBackedStore() {
        RedissonClient redisson = Mockito.mock(RedissonClient.class);
        AgentStateStore store = new SessionStoreConfig().agentStateStore(redisson);
        assertNotNull(store, "AgentStateStore bean should not be null");
        assertTrue(store instanceof RedisAgentStateStore,
                "expected RedisAgentStateStore; got " + store.getClass());
    }
}
