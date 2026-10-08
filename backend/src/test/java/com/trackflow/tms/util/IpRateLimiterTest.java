package com.trackflow.tms.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.trackflow.tms.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    // 10 per minute = one token back every 6 seconds.
    private final IpRateLimiter limiter = new IpRateLimiter(10, Duration.ofMinutes(1), clock);

    @Test
    void allowsTheBurstThenBlocksWithRetryAfter() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1").allowed()).as("request %d", i + 1).isTrue();
        }
        IpRateLimiter.Decision blocked = limiter.tryAcquire("10.0.0.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(6);
    }

    @Test
    void tokensComeBackGradually() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        clock.advance(Duration.ofSeconds(6));
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        }
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isTrue();
    }
}
