package io.github.selcomdevelopers.selcom.webhook;

import io.github.selcomdevelopers.selcom.SelcomException;

/**
 * The replay store failed, so the delivery could not be recorded. Answer the gateway with a 5xx so that it delivers again;
 * do not treat the delivery as verified or as rejected.
 */
public class ReplayStoreException extends SelcomException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param cause what the store threw
     */
    public ReplayStoreException(Throwable cause) {
        super("the replay store failed: " + cause.getClass().getSimpleName(), cause);
    }
}
