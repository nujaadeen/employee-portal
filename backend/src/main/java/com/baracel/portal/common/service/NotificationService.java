package com.baracel.portal.common.service;

import com.baracel.portal.common.domain.Notification;
import com.baracel.portal.common.repo.NotificationRepository;
import com.baracel.portal.common.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final MailService mail;

    public NotificationService(NotificationRepository repo, MailService mail) {
        this.repo = repo;
        this.mail = mail;
    }

    /** Creates the in-app notification and sends the email through Mailpit. */
    @Transactional
    public void notify(UUID employeeId, String email, String type, String title, String body, String link) {
        Notification n = new Notification();
        n.employeeId = employeeId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.link = link;
        repo.save(n);
        mail.send(email, title, body);
    }

    @Transactional(readOnly = true)
    public Page<Notification> page(UUID employeeId, boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return unreadOnly ? repo.findByEmployeeIdAndReadAtIsNull(employeeId, pr) : repo.findByEmployeeId(employeeId, pr);
    }

    /** Marks only the caller's own notifications as read. Returns how many changed. */
    @Transactional
    public int markAllRead(UUID employeeId) { return repo.markAllRead(employeeId, Instant.now()); }

    @Transactional(readOnly = true)
    public long unreadCount(UUID employeeId) { return repo.countByEmployeeIdAndReadAtIsNull(employeeId); }

    @Transactional
    public Notification markRead(UUID employeeId, long id) {
        Notification n = repo.findById(id).filter(x -> x.employeeId.equals(employeeId))
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (n.readAt == null) n.readAt = Instant.now();
        return n;
    }
}
