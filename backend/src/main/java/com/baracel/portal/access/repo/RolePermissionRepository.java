package com.baracel.portal.access.repo;

import com.baracel.portal.access.domain.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleName(String roleName);
    List<RolePermission> findByPermissionId(Long permissionId);
    @Modifying
    @Query("delete from RolePermission rp where rp.permissionId = :id")
    void deleteAllByPermissionId(Long id);

    @Modifying
    @Query("delete from RolePermission rp where rp.roleName = :role")
    void deleteAllByRole(String role);

    @Modifying
    @Query("delete from RolePermission rp where rp.roleName = :role and rp.permissionId in :ids")
    void deleteByRoleAndPermissionIds(String role, java.util.Collection<Long> ids);
}
