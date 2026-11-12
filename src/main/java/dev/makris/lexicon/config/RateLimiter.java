package dev.makris.lexicon.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** In-memory, per-client token-bucket rate limiter with a stricter tier for the expensive LLM endpoints. */
@Component
public class RateLimiter {

    public enum Tier {
        /** Endpoints that call the chat model. */
        ANSWERS,
        /** Everything else under /api. */
        DEFAULT,
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final ConcurrentMap<String, TokenBucket> buckets =
        new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final LongSupplier nanoClock;

    @Autowired
    public RateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    RateLimiter(RateLimitProperties properties, LongSupplier nanoClock) {
        this.properties = properties;
        this.nanoClock = nanoClock;
    }

    public Decision check(String client, Tier tier) {
        long now = nanoClock.getAsLong();
        if (buckets.size() > CLEANUP_THRESHOLD) {
            // A bucket idle this long has fully refilled, so dropping it loses nothing.
            buckets
                .entrySet()
                .removeIf(
                    e -> now - e.getValue().lastTouchedNanos() > IDLE_NANOS
                );
        }
        int perMinute =
            tier == Tier.ANSWERS
                ? properties.answersPerMinute()
                : properties.defaultPerMinute();
        TokenBucket bucket = buckets.computeIfAbsent(tier + ":" + client, key ->
            new TokenBucket(perMinute, now)
        );

        long waitNanos = bucket.tryConsume(now);
        if (waitNanos == 0) {
            return new Decision(true, 0);
        }
        long seconds = Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);
        return new Decision(false, seconds);
    }
}
