package io.github.chenygs.pptagent.chat.middleware;

/**
 * 用于传递当前请求的 threadId 给 ChatPersistenceMiddleware。
 *
 * <p>AG-UI 适配器在调用 agent.stream() 时不传递 RuntimeContext，导致 Middleware
 * 无法通过 {@code agent.getRuntimeContext().getSessionId()} 获取 sessionId。
 * 通过此 ThreadLocal + JDK Proxy 代理 + ThreadSessionManager 组合解决：
 *
 * <ol>
 *   <li>自定义 {@code ThreadSessionManager} 在 {@code getOrCreateAgent()} 中设置本 context</li>
 *   <li>JDK Proxy 代理的 Agent 从本 context 读取 threadId，构建 RuntimeContext</li>
 *   <li>ChatPersistenceMiddleware 从 RuntimeContext 的 sessionId 获取 threadId</li>
 * </ol>
 */
public final class ChatSessionContext {

    private static final ThreadLocal<String> CURRENT_THREAD_ID = new ThreadLocal<>();

    public static void setCurrentThreadId(String threadId) {
        CURRENT_THREAD_ID.set(threadId);
    }

    public static String getCurrentThreadId() {
        return CURRENT_THREAD_ID.get();
    }

    public static void clear() {
        CURRENT_THREAD_ID.remove();
    }

    private ChatSessionContext() {
    }
}
