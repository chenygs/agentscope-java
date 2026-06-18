package io.agentscope.builder.saton.skill.controller;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.service.AgentAccessGuard;
import io.agentscope.builder.saton.agent.orm.enums.Tier;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.skill.orm.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.orm.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.orm.dto.WorkspaceSkillVO;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import io.agentscope.builder.saton.skill.service.AgentSkillService;

@RestController
@RequestMapping("/api/agents/{agentId}/skills")
public class AgentSkillsController {

    private final AgentSkillService skillService;
    private final AgentAccessGuard accessGuard;

    public AgentSkillsController(AgentSkillService skillService,
                                  AgentAccessGuard accessGuard) {
        this.skillService = skillService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/workspace")
    public Mono<R<List<WorkspaceSkillVO>>> listWorkspaceSkills(
            @PathVariable("agentId") Long agentDefId, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.okList(skillService.listWorkspaceSkills(me, agentDefId));
        });
    }

    @DeleteMapping("/workspace/{name}")
    public Mono<R<Void>> deleteWorkspaceSkill(
            @PathVariable("agentId") Long agentDefId,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.EDIT);
            skillService.deleteWorkspaceSkill(me, agentDefId, name);
            return R.ok();
        });
    }

    @PostMapping("/workspace/install")
    public Mono<R<WorkspaceSkillVO>> installFromRepository(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody InstallFromRepoReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.ok(skillService.installFromRepository(me, agentDefId, req));
        });
    }

    @PostMapping("/workspace/marketplace-install")
    public Mono<R<WorkspaceSkillVO>> installFromMarketplace(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody MarketplaceInstallReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.ok(skillService.installFromMarketplace(me, agentDefId, req));
        });
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<R<Void>> uploadSkills(
            @PathVariable("agentId") Long agentDefId,
            @RequestPart("file") Part file,
            @RequestPart("skillName") String skillName,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.EDIT);
            byte[] zipData;
            try {
                zipData = DataBufferUtils.join(file.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                        .block();
            } catch (Exception e) {
                throw new IllegalArgumentException("failed to read uploaded file: " + e.getMessage());
            }
            skillService.uploadSkill(me, agentDefId, skillName,
                    zipData != null ? zipData : new byte[0],
                    file.headers().getContentDisposition() != null
                            ? file.headers().getContentDisposition().getFilename() : "upload.zip");
            return R.ok();
        });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                     java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
