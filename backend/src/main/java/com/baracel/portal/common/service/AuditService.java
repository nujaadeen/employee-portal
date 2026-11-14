package com.baracel.portal.common.service;

import com.baracel.portal.common.domain.AuditLog;
import com.baracel.portal.common.repo.AuditLogRepository;
import com.baracel.portal.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audit trail for access and HR changes. */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional
    public void log(CurrentUser actor, String action, String targetType, String targetId, String detail) {
        AuditLog a = new AuditLog();
        a.actorId = actor == null ? null : actor.employeeId();
        a.actorName = actor == null ? "system" : actor.name();
        a.action = action;
        a.targetType = targetType;
        a.targetId = targetId;
        a.detail = detail;
        repo.save(a);
    }
}
