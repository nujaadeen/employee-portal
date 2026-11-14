package com.baracel.portal.task.domain;

import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ProjectMemberId(Long projectId, UUID employeeId) {}
