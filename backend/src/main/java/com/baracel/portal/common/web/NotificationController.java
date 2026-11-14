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

    public record NotificationDto(long id, String type, String title, String body, String link, boolean read, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.id, n.type, n.title, n.body, n.link, n.readAt != null, n.createdAt);
        }
    }

    @GetMapping
    public List<NotificationDto> list(CurrentUser me) {
        return service.latest(me.employeeId()).stream().map(NotificationDto::of).toList();
    }

    @PostMapping("/{id}/read")
    public NotificationDto read(CurrentUser me, @PathVariable long id) {
        return NotificationDto.of(service.markRead(me.employeeId(), id));
    }
}
