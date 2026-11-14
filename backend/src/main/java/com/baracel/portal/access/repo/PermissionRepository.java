package com.baracel.portal.access.repo;

import com.baracel.portal.access.domain.Permission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByOrderBySortOrderAsc();
}
