package io.agentscope.builder.saton.memory;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.memory.dto.MemorySummaryVO;
import io.agentscope.builder.saton.memory.dto.WriteMemoryReq;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 用户级记忆文件 REST 接口。
 *
 * <p>资源型路径,{@code kind} 是 {@link MemoryKind#slug()} (如 {@code persona} /
 * {@code long-term})。文件物理位置在 {@code <workspace.root>/<ownerId>/} 下,
 * 跨该用户的所有 agent 共享(对齐 AgentScope 2.0 Harness 设计)。
 *
 * <ul>
 *   <li>{@code GET  /api/memory}            — 摘要(各 kind 的存在性 / 大小 / 修改时间)</li>
 *   <li>{@code GET  /api/memory/{kind}}     — 读取(text/plain;不存在返回空串)</li>
 *   <li>{@code PUT  /api/memory/{kind}}     — 全文覆写</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memory;

    public MemoryController(MemoryService memory) {
        this.memory = memory;
    }

    @GetMapping
    public Mono<R<MemorySummaryVO>> summary(ServerWebExchange ex) {
        return scoped(ex, me -> R.ok(memory.summary(me)));
    }

    @GetMapping(value = "/{kind}", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> read(@PathVariable("kind") String kind, ServerWebExchange ex) {
        // Returns raw text — not wrapped in R<T> because produces=text/plain.
        return scoped(ex, me -> memory.read(me, MemoryKind.fromSlug(kind)));
    }

    @PutMapping("/{kind}")
    public Mono<R<Void>> write(@PathVariable("kind") String kind,
                               @RequestBody(required = false) WriteMemoryReq req,
                               ServerWebExchange ex) {
        return scoped(ex, me -> {
            memory.write(me, MemoryKind.fromSlug(kind), req == null ? "" : req.content());
            return R.ok();
        });
    }

    /** Bind sa-token context inside the lambda (spec §12.4) and clear on exit. */
    private <T> Mono<T> scoped(ServerWebExchange ex, Function<String, T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(ex);
            try {
                return body.apply(StpUtil.getLoginIdAsString());
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }
}
