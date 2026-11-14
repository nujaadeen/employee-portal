package com.baracel.portal.access.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permission")
public class RolePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "role_name") public String roleName;
    @Column(name = "permission_id") public Long permissionId;

    public RolePermission() {}
    public RolePermission(String roleName, Long permissionId) { this.roleName = roleName; this.permissionId = permissionId; }
}
