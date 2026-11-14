package com.baracel.portal.access.repo;

import com.baracel.portal.access.domain.CustomRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomRoleRepository extends JpaRepository<CustomRole, String> {
    List<CustomRole> findAllByOrderByNameAsc();
}
