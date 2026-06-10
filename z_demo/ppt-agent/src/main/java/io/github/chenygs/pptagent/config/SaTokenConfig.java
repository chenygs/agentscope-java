package io.github.chenygs.pptagent.config;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：
 * <ul>
 *   <li>使用 SaServletFilter（不是 SaInterceptor）：在 Servlet Filter 层早期完成鉴权，
 *       不会因为 SSE / 异步分发触发 "SaTokenContext 上下文尚未初始化" 错误</li>
 *   <li>放行登录、健康检查、静态资源</li>
 *   <li>其余所有接口（含 /agui/run、/api/user/info 等）都需要登录</li>
 *   <li>全局 CORS</li>
 * </ul>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 全局过滤器。
     *
     * <p>用 Filter 而不是 Interceptor 的原因：
     * <ul>
     *   <li>Filter 执行时机比 Interceptor 早，能在请求转 async 之前完成鉴权</li>
     *   <li>SSE / @Async / DeferredResult 这种异步分发不会二次触发 Filter</li>
     *   <li>Interceptor 在 Spring MVC 异步分发时可能再次执行，此时上下文已脱离原 Servlet 线程</li>
     * </ul>
     */
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                // 拦截所有
                .addInclude("/**")
                // 放行不需要登录的接口
                .addExclude(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/",
                        "/index.html",
                        "/ppt-builder.html",
                        "/favicon.ico",
                        "/js/**",
                        "/css/**",
                        "/assets/**"
                )
                // 鉴权动作
                .setAuth(obj -> SaRouter.match("/**").check(r -> StpUtil.checkLogin()))
                // 异常处理：返回 401 + JSON
                .setError(e -> SaResult.error(e.getMessage()).setCode(401));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .exposedHeaders("Authorization")
                .maxAge(3600);
    }
}
