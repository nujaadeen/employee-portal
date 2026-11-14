package com.baracel.portal.common.web;

import com.baracel.portal.common.domain.Notification;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    public record NotificationDto(long id, String type, String title, @Nullable String body, @Nullable String link, boolean read, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.id, n.type, n.title, n.body, n.link, n.readAt != null, n.createdAt);
        }
    }

    public record NotificationPage(List<NotificationDto> items, long total, long unreadCount, int page, int size) {}
    public record ReadAllResult(int updated) {}

    @GetMapping
    public NotificationPage list(CurrentUser me, @RequestParam(defaultValue = "false") boolean unread,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.page(me.employeeId(), unread, page, size);
        return new NotificationPage(result.getContent().stream().map(NotificationDto::of).toList(), result.getTotalElements(),
                service.unreadCount(me.employeeId()), result.getNumber(), result.getSize());
    }

    @PostMapping("/read-all")
    public ReadAllResult readAll(CurrentUser me) { return new ReadAllResult(service.markAllRead(me.employeeId())); }

    @PostMapping("/{id}/read")
    public NotificationDto read(CurrentUser me, @PathVariable long id) {
        return NotificationDto.of(service.markRead(me.employeeId(), id));
    }
}
