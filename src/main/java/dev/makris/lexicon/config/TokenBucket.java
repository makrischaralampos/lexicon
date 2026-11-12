package dev.makris.lexicon.config;

/** A token bucket: holds up to {@code perMinute} tokens and refills continuously. Thread-safe. */
final class TokenBucket {

    private static final double NANOS_PER_MINUTE = 60_000_000_000.0;

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastNanos;

    TokenBucket(int perMinute, long nowNanos) {
        this.capacity = perMinute;
        this.refillPerNano = perMinute / NANOS_PER_MINUTE;
        this.tokens = capacity;
        this.lastNanos = nowNanos;
    }

    /** Returns 0 when a token was taken, otherwise the nanoseconds until one will be available. */
    synchronized long tryConsume(long nowNanos) {
        tokens = Math.min(
            capacity,
            tokens + Math.max(0, nowNanos - lastNanos) * refillPerNano
        );
        lastNanos = nowNanos;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return 0;
        }
        return (long) Math.ceil((1.0 - tokens) / refillPerNano);
    }

    synchronized long lastTouchedNanos() {
        return lastNanos;
    }
}
