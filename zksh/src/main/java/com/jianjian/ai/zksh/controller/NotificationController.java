package com.jianjian.ai.zksh.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.domain.dto.CreateNotificationDTO;
import com.jianjian.ai.zksh.domain.dto.CreateReminderRuleDTO;
import com.jianjian.ai.zksh.domain.vo.NotificationVO;
import com.jianjian.ai.zksh.domain.vo.ReminderRuleVO;
import com.jianjian.ai.zksh.security.UserContext;
import com.jianjian.ai.zksh.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/notify")
public class NotificationController {
    private final ScheduledExecutorService sseExecutor = Executors.newScheduledThreadPool(1);

    @Autowired
    private NotificationService notificationService;

    @PostMapping
    public ApiResponse<NotificationVO> create(@Valid @RequestBody CreateNotificationDTO dto) {
        return ApiResponse.ok(notificationService.create(UserContext.getUserId(), dto));
    }

    @GetMapping
    public ApiResponse<List<NotificationVO>> list(@RequestParam(value = "isRead", required = false) Integer isRead) {
        return ApiResponse.ok(notificationService.list(UserContext.getUserId(), isRead));
    }

    @GetMapping("/unread/changes")
    public ApiResponse<List<NotificationVO>> listUnreadChanges(
            @RequestParam(value = "lastId", required = false, defaultValue = "0") Long lastId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return ApiResponse.ok(notificationService.listUnreadAfterId(UserContext.getUserId(), lastId, limit));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUnread(
            @RequestParam(value = "lastId", required = false, defaultValue = "0") Long lastId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        Long userId = UserContext.getUserId();
        SseEmitter emitter = new SseEmitter(180_000L);
        final long[] cursor = {lastId == null ? 0L : Math.max(lastId, 0L)};

        Runnable task = () -> {
            try {
                List<NotificationVO> changes = notificationService.listUnreadAfterId(userId, cursor[0], limit);
                if (!changes.isEmpty()) {
                    cursor[0] = changes.get(changes.size() - 1).id();
                    emitter.send(SseEmitter.event().name("notify").data(changes));
                } else {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis())));
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        };

        ScheduledFuture<?> future = sseExecutor.scheduleAtFixedRate(task, 0, 5, TimeUnit.SECONDS);
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> {
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError((ex) -> {
            future.cancel(true);
            emitter.complete();
        });
        return emitter;
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Map<String, String>> read(@PathVariable Long id) {
        notificationService.read(UserContext.getUserId(), id);
        return ApiResponse.ok(Map.of("message", "已标记已读"));
    }

    @PostMapping("/rules")
    public ApiResponse<ReminderRuleVO> createRule(@Valid @RequestBody CreateReminderRuleDTO dto) {
        return ApiResponse.ok(notificationService.createRule(UserContext.getUserId(), dto));
    }

    @GetMapping("/rules")
    public ApiResponse<List<ReminderRuleVO>> listRules() {
        return ApiResponse.ok(notificationService.listRules(UserContext.getUserId()));
    }
}
