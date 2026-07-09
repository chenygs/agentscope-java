package com.brainmed.ai.qa.web;

import com.brainmed.ai.qa.core.util.JwtTokenUtils;
import com.brainmed.ai.qa.orm.vo.ChatMessageVo;
import com.brainmed.ai.qa.orm.vo.PinResultVo;
import com.brainmed.ai.qa.orm.vo.SessionSummaryVo;
import com.brainmed.ai.qa.orm.vo.ShareInfoVo;
import com.brainmed.ai.qa.service.ConversationMessageService;
import com.brainmed.ai.qa.web.resp.R;
import com.brainmed.ai.qa.web.resp.list.ListData;
import com.brainmed.ai.qa.web.resp.page.Pager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 对话历史查询接口（供前端读库替换 localStorage）。
 *
 * <p>查询是阻塞 JPA，本项目为 WebFlux，故一律 {@code Mono.fromCallable(...)} +
 * {@link Schedulers#boundedElastic()}，不在 event loop 上执行阻塞 IO。
 */
@RestController
public class QaHistoryController {

    private final ConversationMessageService service;

    public QaHistoryController(ConversationMessageService service) {
        this.service = service;
    }

    /** 某会话的历史消息（分页，AG-UI 格式）。 */
    @GetMapping("/qa/history")
    public Mono<R<Pager<ChatMessageVo>>> history(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String userId = JwtTokenUtils.resolveUserId();
        Pageable pageable = PageRequest.of(current - 1, size,
                Sort.by("createdAt").ascending()
                        .and(Sort.by("seq").ascending())
                        .and(Sort.by("id").ascending()));
        return Mono.fromCallable(() -> service.historyPage(userId, sessionId, pageable))
                .map(R::ok)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 当前用户的会话 id 列表。 */
    @GetMapping("/qa/sessions")
    public Mono<R<ListData<String>>> sessions() {
        String userId = JwtTokenUtils.resolveUserId();
        return Mono.fromCallable(() -> service.sessionIds(userId))
                .map(R::list)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 当前用户的会话摘要列表（含标题 + 最后活跃时间，按时间倒序）。 */
    @GetMapping("/qa/sessions/summary")
    public Mono<R<ListData<SessionSummaryVo>>> sessionSummaries() {
        String userId = JwtTokenUtils.resolveUserId();
        return Mono.fromCallable(() -> service.sessionSummaries(userId))
                .map(R::list)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除某会话的所有消息。 */
    @DeleteMapping("/qa/sessions/{sessionId}")
    public Mono<R<Void>> deleteSession(@PathVariable String sessionId) {
        String userId = JwtTokenUtils.resolveUserId();
        return Mono.fromRunnable(() -> service.deleteSession(userId, sessionId))
                .then(Mono.just(R.<Void>ok()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 分享某会话，生成只读链接。 */
    @PostMapping("/qa/sessions/{sessionId}/share")
    public Mono<R<ShareInfoVo>> shareSession(@PathVariable String sessionId) {
        String userId = JwtTokenUtils.resolveUserId();
        return Mono.fromCallable(() -> service.shareSession(userId, sessionId))
                .map(R::ok)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 切换会话置顶状态。 */
    @PostMapping("/qa/sessions/{sessionId}/pin")
    public Mono<R<PinResultVo>> togglePin(@PathVariable String sessionId) {
        String userId = JwtTokenUtils.resolveUserId();
        return Mono.fromCallable(() -> new PinResultVo(service.togglePin(userId, sessionId)))
                .map(R::ok)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 公开接口：根据 token 获取分享对话的消息列表（无需登录）。 */
    @GetMapping("/qa/share/{token}")
    public Mono<R<ListData<ChatMessageVo>>> sharedConversation(@PathVariable String token) {
        return Mono.fromCallable(() -> {
            java.util.List<ChatMessageVo> msgs = service.getSharedMessages(token);
            if (msgs == null) {
                return R.list(java.util.Collections.<ChatMessageVo>emptyList());
            }
            return R.list(msgs);
        }).subscribeOn(Schedulers.boundedElastic());
    }


}
