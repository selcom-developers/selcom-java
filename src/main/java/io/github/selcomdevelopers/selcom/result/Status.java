package io.github.selcomdevelopers.selcom.result;

/** The five typed statuses every operation returns. */
public enum Status {
    /** It happened. Fulfil only after confirming (see the webhook guidance). Final. */
    SUCCESS(true),
    /** Accepted, still processing. Wait for the webhook, or query after 3 minutes or more. */
    PENDING(false),
    /** Nobody knows yet. Query after 3 minutes or more. Never resubmit. */
    AMBIGUOUS(false),
    /** It did not happen. A new attempt is a new transaction with a new ID, and it is the caller's decision. Final. */
    FAILED(true),
    /** The gateway has no record of the ID. The only case in which the same ID may be submitted again. Final. */
    NOT_FOUND(true);

    private final boolean isFinal;

    Status(boolean isFinal) {
        this.isFinal = isFinal;
    }

    /**
     * Whether this status will not change on a later query.
     *
     * @return true for {@link #SUCCESS}, {@link #FAILED} and {@link #NOT_FOUND}
     */
    public boolean isFinal() {
        return isFinal;
    }
}
