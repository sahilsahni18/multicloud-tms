package com.trackflow.tms.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Bucket4j token bucket per key (client IP): {@code capacity} requests, refilled
 * evenly over {@code period}. Buckets live in a bounded Caffeine cache and are
 * dropped after two idle periods, so memory stays flat under many clients.
 */
public class IpRateLimiter {

    private static final long MAX_TRACKED_KEYS = 100_000;

    private final int capacity;
    private final Duration period;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    public IpRateLimiter(int capacity, Duration period, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        this.period = period;
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(period.multipliedBy(2))
                .build();
    }

    public Decision tryAcquire(String key) {
        Bucket bucket = buckets.get(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.ALLOWED;
        }
        long waitNanos = probe.getNanosToWaitForRefill();
        return new Decision(false, Math.max(1, (waitNanos + 999_999_999) / 1_000_000_000));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    /** Lets Bucket4j read time from the injected Clock, so tests can move time. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
