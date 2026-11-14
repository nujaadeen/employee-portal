package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.SocialChannel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialChannelRepository extends JpaRepository<SocialChannel, Long> {
    List<SocialChannel> findByCodeIn(Collection<String> codes);
    List<SocialChannel> findAllByOrderByIdAsc();
}
