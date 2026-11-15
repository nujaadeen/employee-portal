package com.baracel.portal.ai.service;

import com.baracel.portal.ai.client.AiProperties;
import com.baracel.portal.common.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Sliding window: at most N chat messages per minute per person. Over the limit: 429 with Retry-After. */
@Component
public class ChatRateLimiter {
    private final int limit;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public ChatRateLimiter(AiProperties props, Clock clock) {
        this.limit = props.rateLimit().chatPerMinute();
        this.clock = clock;
    }

    public void check(UUID user) {
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(user, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && Duration.between(q.peekFirst(), now).compareTo(Duration.ofMinutes(1)) >= 0) q.pollFirst();
            if (q.size() >= limit) {
                long wait = Math.max(1, 60 - Duration.between(q.peekFirst(), now).toSeconds());
                throw ApiException.tooManyRequests("You can send " + limit + " messages per minute. Try again in " + wait + " seconds.", (int) wait);
            }
            q.addLast(now);
        }
    }
}
