package io.github.selcomdevelopers.selcom;

import java.time.Duration;
import java.util.Objects;

/**
 * A timeout profile. No operation runs without one.
 *
 * @param connect how long to wait for the connection
 * @param read how long to wait for the response once the request is sent
 */
public record Timeouts(Duration connect, Duration read) {
    /** The default for queries and everything that moves no money: 10 s and 10 s. */
    public static final Timeouts QUERY = new Timeouts(Duration.ofSeconds(10), Duration.ofSeconds(10));
    /** The default for money-moving operations: 15 s and 180 s. Timing out early does not stop a payment. */
    public static final Timeouts PAYMENT = new Timeouts(Duration.ofSeconds(15), Duration.ofSeconds(180));

    /** Validates that both durations are positive. */
    public Timeouts {
        Objects.requireNonNull(connect, "connect");
        Objects.requireNonNull(read, "read");
        if (connect.isZero() || connect.isNegative() || read.isZero() || read.isNegative()) {
            throw new ConfigurationException("timeouts must be positive");
        }
    }
}
