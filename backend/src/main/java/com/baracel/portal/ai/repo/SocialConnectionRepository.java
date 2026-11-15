package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.SocialConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialConnectionRepository extends JpaRepository<SocialConnection, UUID> {
    List<SocialConnection> findByOwnerIdAndStatusNot(UUID ownerId, String status);
    List<SocialConnection> findByOwnerIdIsNullAndStatusNot(String status);
    Optional<SocialConnection> findFirstByOwnerIdAndProviderAndStatusNot(UUID ownerId, String provider, String status);
    Optional<SocialConnection> findFirstByOwnerIdIsNullAndProviderAndStatusNot(String provider, String status);
    Optional<SocialConnection> findFirstByOwnerIdIsNullAndProviderAndAccountUrnAndStatusNot(String provider, String urn, String status);
}
