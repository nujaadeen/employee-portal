package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.SocialOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialOAuthStateRepository extends JpaRepository<SocialOAuthState, String> {}
