package com.trackflow.tms.util;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fixed-window counter: at most {@code limit} hits per key per window. */
public class FixedWindowRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public Decision tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now >= w.start + windowMillis);
        }
        Window window = windows.compute(key, (k, current) ->
                current == null || now >= current.start + windowMillis
                        ? new Window(now, 1)
                        : new Window(current.start, current.count + 1));
        if (window.count <= limit) {
            return Decision.ALLOWED;
        }
        long retryAfterMillis = window.start + windowMillis - now;
        return new Decision(false, Math.max(1, (retryAfterMillis + 999) / 1000));
    }

    private record Window(long start, int count) {
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }
}
