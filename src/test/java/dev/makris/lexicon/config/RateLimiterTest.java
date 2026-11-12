package dev.makris.lexicon.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong clock = new AtomicLong();

    private RateLimiter limiter(int answersPerMinute) {
        return new RateLimiter(
            new RateLimitProperties(true, 120, answersPerMinute),
            clock::get
        );
    }

    @Test
    void allowsUpToTheLimitThenRejectsWithARetryHint() {
        RateLimiter limiter = limiter(3);

        for (int i = 0; i < 3; i++) {
            assertThat(
                limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
            ).isTrue();
        }
        RateLimiter.Decision rejected = limiter.check(
            "client",
            RateLimiter.Tier.ANSWERS
        );

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    void tokensRefillOverTime() {
        RateLimiter limiter = limiter(3);
        for (int i = 0; i < 3; i++) {
            limiter.check("client", RateLimiter.Tier.ANSWERS);
        }
        assertThat(
            limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
        ).isFalse();

        clock.addAndGet(TimeUnit.SECONDS.toNanos(21));

        assertThat(
            limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
        ).isTrue();
    }

    @Test
    void clientsAndTiersHaveIndependentBuckets() {
        RateLimiter limiter = limiter(1);
        assertThat(
            limiter.check("a", RateLimiter.Tier.ANSWERS).allowed()
        ).isTrue();
        assertThat(
            limiter.check("a", RateLimiter.Tier.ANSWERS).allowed()
        ).isFalse();

        assertThat(
            limiter.check("b", RateLimiter.Tier.ANSWERS).allowed()
        ).isTrue();
        assertThat(
            limiter.check("a", RateLimiter.Tier.DEFAULT).allowed()
        ).isTrue();
    }

    @Test
    void bucketsNeverHoldMoreThanTheirCapacity() {
        RateLimiter limiter = limiter(2);
        clock.addAndGet(TimeUnit.HOURS.toNanos(5));

        assertThat(
            limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
        ).isTrue();
        assertThat(
            limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
        ).isTrue();
        assertThat(
            limiter.check("client", RateLimiter.Tier.ANSWERS).allowed()
        ).isFalse();
    }
}
