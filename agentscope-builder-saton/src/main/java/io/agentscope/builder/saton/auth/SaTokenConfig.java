package io.agentscope.builder.saton.auth;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfig {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/auth/login",
                        "/actuator/health",
                        "/actuator/info",
                        "/favicon.ico"
                )
                .setAuth(obj -> SaRouter.match("/**").check(r -> StpUtil.checkLogin()));
    }
}
