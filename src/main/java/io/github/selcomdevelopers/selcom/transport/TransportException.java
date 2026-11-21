package io.github.selcomdevelopers.selcom.transport;

import io.github.selcomdevelopers.selcom.SelcomException;
import java.util.Objects;

/**
 * No HTTP response was received. This is never disguised as a gateway response and never becomes a FAILED status.
 */
public class TransportException extends SelcomException {
    private static final long serialVersionUID = 1L;

    /** What the caller may conclude about the request. */
    public enum Outcome {
        /**
         * DNS resolution failed, the connection was refused or timed out, the TLS handshake failed, or the proxy
         * refused. The request provably never reached the gateway; sending it again with the same ID is safe.
         */
        NOT_SENT,
        /**
         * Anything after the request may have been written: a read or write timeout, a reset connection, a truncated
         * response. The gateway may have acted. Treat as AMBIGUOUS: query, never resend.
         */
        UNKNOWN_OUTCOME
    }

    private final Outcome outcome;

    /**
     * Creates the exception.
     *
     * @param outcome what the caller may conclude
     * @param message what happened; never contains a secret
     * @param cause the underlying failure, or null
     */
    public TransportException(Outcome outcome, String message, Throwable cause) {
        super(Objects.requireNonNull(outcome, "outcome") + ": " + message, cause);
        this.outcome = outcome;
    }

    /**
     * What the caller may conclude about the request.
     *
     * @return the classification
     */
    public Outcome outcome() {
        return outcome;
    }
}
