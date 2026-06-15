package io.agentscope.builder.saton.workspace;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import io.agentscope.builder.saton.workspace.dto.WriteFileReq;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{id}/workspace")
public class WorkspaceController {

    private final WorkspaceService workspace;
    private final AgentDefinitionRepository agentRepo;

    public WorkspaceController(WorkspaceService workspace, AgentDefinitionRepository agentRepo) {
        this.workspace = workspace;
        this.agentRepo = agentRepo;
    }

    @GetMapping
    public Mono<R<WorkspaceSummaryVO>> summary(@PathVariable("id") Long id, ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return R.ok(workspace.summary(me, id));
        });
    }

    @GetMapping("/files")
    public Mono<R<List<FileNodeVO>>> list(@PathVariable("id") Long id,
                                          @RequestParam(value = "path", required = false) String path,
                                          ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return R.okList(workspace.listAt(me, id, path));
        });
    }

    @GetMapping(value = "/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> read(@PathVariable("id") Long id,
                             @RequestParam("path") String path,
                             ServerWebExchange ex) {
        // Returns raw text — not wrapped in R<T> because produces=text/plain
        return scoped(ex, me -> {
            requireOwn(id, me);
            return workspace.read(me, id, path);
        });
    }

    @PutMapping("/file")
    public Mono<R<Void>> write(@PathVariable("id") Long id,
                               @RequestParam("path") String path,
                               @RequestBody WriteFileReq req,
                               ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            workspace.write(me, id, path, req == null ? "" : req.content());
            return R.ok();
        });
    }

    @DeleteMapping("/file")
    public Mono<R<Boolean>> delete(@PathVariable("id") Long id,
                                   @RequestParam("path") String path,
                                   ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return R.ok(workspace.delete(me, id, path));
        });
    }

    private void requireOwn(Long agentDefId, String me) {
        if (agentRepo.findByIdAndOwnerId(agentDefId, me).isEmpty()) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
    }

    /** Bind sa-token context inside the lambda (spec §12.4) and clear on exit. */
    private <T> Mono<T> scoped(ServerWebExchange ex,
                               java.util.function.Function<String, T> body) {
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
