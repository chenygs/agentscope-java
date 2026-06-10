package io.github.chenygs.pptagent.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 为 HarnessAgent 创建 JDK 动态代理，拦截 {@code stream(msgs, options)} 调用，
 * 将 ChatSessionContext 中的 threadId 注入 RuntimeContext。
 *
 * <p>AG-UI 适配器调用 {@code agent.stream(msgs, options)}（无 RuntimeContext 参数），
 * 导致 Middleware 无法获取 sessionId。此代理截获该调用，改为调用
 * {@code agent.stream(msgs, options, RuntimeContext)}，实现 sessionId 透传。
 */
@Slf4j
public class SessionAwareAgentProxy implements InvocationHandler {

    private final HarnessAgent target;

    private SessionAwareAgentProxy(HarnessAgent target) {
        this.target = target;
    }

    /**
     * 为 HarnessAgent 创建 JDK 动态代理实例。
     */
    @SuppressWarnings("unchecked")
    public static <T extends Agent> T create(HarnessAgent target) {
        return (T) Proxy.newProxyInstance(
                Agent.class.getClassLoader(),
                new Class<?>[]{Agent.class},
                new SessionAwareAgentProxy(target));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 拦截 stream(msgs, options) — 注入 RuntimeContext
        if ("stream".equals(method.getName()) && args != null && args.length == 2
                && args[0] instanceof List && args[1] instanceof StreamOptions) {

            @SuppressWarnings("unchecked")
            List<Msg> msgs = (List<Msg>) args[0];
            StreamOptions options = (StreamOptions) args[1];

            String threadId = ChatSessionContext.getCurrentThreadId();
            if (threadId != null && !threadId.isEmpty()) {
                // 解析 threadId = "userId:sessionId"，分别注入 userId 和 sessionId
                String userId = null;
                String sessionId = threadId;
                if (threadId.contains(":")) {
                    String[] parts = threadId.split(":", 2);
                    userId = parts[0];
                    sessionId = parts[1]; // 纯数字 session ID，不含冒号，文件路径安全
                }
                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .build();
                log.debug("Proxy: injecting RuntimeContext with sessionId={}, userId={}",
                        sessionId, userId);
                return target.stream(msgs, options, ctx);
            } else {
                log.warn("Proxy: no threadId in ChatSessionContext, calling stream() without context");
                return target.stream(msgs, options);
            }
        }

        // 其它方法直接透传
        return method.invoke(target, args);
    }
}
