package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.hook.HookFactory;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.skill.SkillFactory;
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
 * {@code /skill-repo-types}, {@code /hook-types}。
 *
 * <p>所有端点都不调 {@link cn.dev33.satoken.stp.StpUtil}（只读 in-memory 注册表），
 * 因此不需要 {@code SaReactorSyncHolder} 上下文绑定;sa-token 全局过滤器负责"必须登录"。
 */
@RestController
@RequestMapping("/api/factories")
public class FactoriesController {

    private final ModelFactory modelFactory;
    private final io.agentscope.builder.saton.factory.tool.ToolFactory toolFactory;
    private final SkillFactory skillFactory;
    private final HookFactory hookFactory;

    public FactoriesController(ModelFactory modelFactory,
                               io.agentscope.builder.saton.factory.tool.ToolFactory toolFactory,
                               SkillFactory skillFactory,
                               HookFactory hookFactory) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.skillFactory = skillFactory;
        this.hookFactory = hookFactory;
    }

    @GetMapping("/model-types")
    public Mono<List<TypeMeta>> modelTypes() {
        return Mono.fromCallable(modelFactory::listTypes);
    }

    @GetMapping("/tool-types")
    public Mono<List<TypeMeta>> toolTypes() {
        return Mono.fromCallable(toolFactory::listTypes);
    }

    @GetMapping("/skill-repo-types")
    public Mono<List<TypeMeta>> skillRepoTypes() {
        return Mono.fromCallable(skillFactory::listTypes);
    }

    @GetMapping("/hook-types")
    public Mono<List<TypeMeta>> hookTypes() {
        return Mono.fromCallable(hookFactory::listTypes);
    }
}
