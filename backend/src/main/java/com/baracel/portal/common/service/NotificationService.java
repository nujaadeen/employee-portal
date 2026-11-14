package com.baracel.portal.common.service;

import com.baracel.portal.common.domain.Notification;
import com.baracel.portal.common.repo.NotificationRepository;
import com.baracel.portal.common.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    public List<Notification> latest(UUID employeeId) { return repo.findTop50ByEmployeeIdOrderByCreatedAtDesc(employeeId); }

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
