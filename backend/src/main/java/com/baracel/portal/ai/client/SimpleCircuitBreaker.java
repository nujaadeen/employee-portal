package com.baracel.portal.ai.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Small circuit breaker for the AI service. CLOSED: calls pass. After {@code threshold} failures in a row it is OPEN and
 * refuses calls for {@code openFor}. Then one trial call is allowed (HALF_OPEN): success closes it, failure re-opens it.
 */
public class SimpleCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int threshold;
    private final Duration openFor;
    private final Clock clock;
    private int failures;
    private Instant openedAt;
    private boolean trialInFlight;

    public SimpleCircuitBreaker(int threshold, Duration openFor, Clock clock) {
        this.threshold = threshold;
        this.openFor = openFor;
        this.clock = clock;
    }

    /** True when a call may go through. Call {@link #success()} or {@link #failure()} afterwards. */
    public synchronized boolean tryAcquire() {
        if (openedAt == null) return true;
        if (Duration.between(openedAt, clock.instant()).compareTo(openFor) < 0) return false;
        if (trialInFlight) return false;
        trialInFlight = true;   // half-open: exactly one trial
        return true;
    }

    public synchronized void success() {
        failures = 0;
        openedAt = null;
        trialInFlight = false;
    }

    public synchronized void failure() {
        trialInFlight = false;
        if (openedAt != null || ++failures >= threshold) openedAt = clock.instant();
    }

    public synchronized State state() {
        if (openedAt == null) return State.CLOSED;
        return Duration.between(openedAt, clock.instant()).compareTo(openFor) < 0 ? State.OPEN : State.HALF_OPEN;
    }
}
