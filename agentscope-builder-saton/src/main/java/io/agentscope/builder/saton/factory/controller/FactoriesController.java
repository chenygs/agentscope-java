package io.agentscope.builder.saton.factory.controller;

import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.middleware.MiddlewareFactory;
import io.agentscope.builder.saton.factory.service.model.ModelFactory;
import io.agentscope.builder.saton.factory.service.skill.SkillFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 给前端的"工厂目录"接口。未来 Agent 类型也会在这里挂端点
 * （/agent-types），形成统一的 5 大类目录。
 *
 * <p>当前已暴露：{@code /model-types}, {@code /tool-types},
 * {@code /skill-repo-types}, {@code /middleware-types}。
 *
 * <p>所有端点都不调 {@link cn.dev33.satoken.stp.StpUtil}（只读 in-memory 注册表），
 * 因此不需要 {@code SaReactorSyncHolder} 上下文绑定;sa-token 全局过滤器负责"必须登录"。
 */
@RestController
@RequestMapping("/api/factories")
public class FactoriesController {

    private final ModelFactory modelFactory;
    private final io.agentscope.builder.saton.factory.service.tool.ToolFactory toolFactory;
    private final SkillFactory skillFactory;
    private final MiddlewareFactory middlewareFactory;

    public FactoriesController(ModelFactory modelFactory,
                               io.agentscope.builder.saton.factory.service.tool.ToolFactory toolFactory,
                               SkillFactory skillFactory,
                               MiddlewareFactory middlewareFactory) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.skillFactory = skillFactory;
        this.middlewareFactory = middlewareFactory;
    }

    @GetMapping("/model-types")
    public Mono<R<List<TypeMeta>>> modelTypes() {
        return Mono.fromCallable(() -> R.okList(modelFactory.listTypes()));
    }

    @GetMapping("/tool-types")
    public Mono<R<List<TypeMeta>>> toolTypes() {
        return Mono.fromCallable(() -> R.okList(toolFactory.listTypes()));
    }

    @GetMapping("/builtin-tools")
    public Mono<R<List<TypeMeta>>> builtinTools() {
        return Mono.fromCallable(() -> R.okList(toolFactory.listBuiltinTools()));
    }

    @GetMapping("/skill-repo-types")
    public Mono<R<List<TypeMeta>>> skillRepoTypes() {
        return Mono.fromCallable(() -> R.okList(skillFactory.listTypes()));
    }

    @GetMapping("/middleware-types")
    public Mono<R<List<TypeMeta>>> middlewareTypes() {
        return Mono.fromCallable(() -> R.okList(middlewareFactory.listTypes()));
    }
}
