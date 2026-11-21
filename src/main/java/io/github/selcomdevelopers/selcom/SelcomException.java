package io.github.selcomdevelopers.selcom;

/** Base class of every exception this library throws on purpose. */
public class SelcomException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message what went wrong; never contains a secret
     */
    public SelcomException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message what went wrong; never contains a secret
     * @param cause the underlying failure
     */
    public SelcomException(String message, Throwable cause) {
        super(message, cause);
    }
}
