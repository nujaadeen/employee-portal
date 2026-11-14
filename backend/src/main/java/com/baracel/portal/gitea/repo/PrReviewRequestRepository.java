package com.baracel.portal.gitea.repo;

import com.baracel.portal.gitea.domain.PrReviewRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrReviewRequestRepository extends JpaRepository<PrReviewRequest, Long> {
    Optional<PrReviewRequest> findByRepoAndPrNumberAndReviewerUsernameIgnoreCase(String repo, int prNumber, String reviewer);
    List<PrReviewRequest> findByRepoAndPrNumber(String repo, int prNumber);
    List<PrReviewRequest> findByReviewerUsernameIgnoreCaseAndStateOrderByUpdatedAtDesc(String reviewer, String state);
}
