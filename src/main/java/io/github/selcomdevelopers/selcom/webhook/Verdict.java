package io.github.selcomdevelopers.selcom.webhook;

/** The outcome of verifying one webhook delivery. */
public enum Verdict {
    /** Signed correctly, fresh, and not seen before. Respond 200, process, then confirm with order-status. */
    VALID(200, true),
    /** No {@code Digest} header. Nothing to check: the order-status confirmation is the only protection. Respond 200, process. */
    UNSIGNED(200, true),
    /** Already handled within the window. Respond 200 and do not process. */
    REPLAY(200, false),
    /** Malformed, tampered or signed with another key. Respond 401, do not process, log it redacted. */
    INVALID(401, false),
    /** Correctly signed but outside the time window. Respond 401, do not process, log it redacted. */
    STALE(401, false);

    private final int httpStatus;
    private final boolean process;

    Verdict(int httpStatus, boolean process) {
        this.httpStatus = httpStatus;
        this.process = process;
    }

    /**
     * The HTTP status to answer the gateway with.
     *
     * @return 200 or 401
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Whether the delivery should be processed (after the order-status confirmation).
     *
     * @return true for VALID and UNSIGNED
     */
    public boolean shouldProcess() {
        return process;
    }
}
