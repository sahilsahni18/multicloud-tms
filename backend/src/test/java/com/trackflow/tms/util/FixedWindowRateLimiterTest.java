package com.trackflow.tms.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.trackflow.tms.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitThenBlocksWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(15));

        FixedWindowRateLimiter.Decision blocked = limiter.tryAcquire("10.0.0.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(45);
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isTrue();
    }

    @Test
    void windowResetsAfterOneMinute() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
    }
}
