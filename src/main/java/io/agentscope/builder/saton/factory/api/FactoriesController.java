package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 给前端的"工厂目录"接口。未来 Tool / Skill / Hook / Agent 类型也会在这里挂端点
 * （/tool-types / /skill-repo-types / /hook-types / /agent-types），形成统一的 5 大类目录。
 *
 * <p>本里程碑（M3）仅暴露 {@code /api/factories/model-types}。
 *
 * <p>所有端点都不调 {@link cn.dev33.satoken.stp.StpUtil}（只读 in-memory 注册表），
 * 因此不需要 {@code SaReactorSyncHolder} 上下文绑定；sa-token 全局过滤器负责"必须登录"。
 */
@RestController
@RequestMapping("/api/factories")
public class FactoriesController {

    private final ModelFactory modelFactory;

    public FactoriesController(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @GetMapping("/model-types")
    public Mono<List<TypeMeta>> modelTypes() {
        return Mono.fromCallable(modelFactory::listTypes);
    }
}
