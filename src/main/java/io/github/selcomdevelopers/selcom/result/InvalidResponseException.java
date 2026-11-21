package io.github.selcomdevelopers.selcom.result;

import io.github.selcomdevelopers.selcom.SelcomException;

/**
 * A response carried no verdict, and the operation does not move money, so it cannot be classified.
 * Raised only for operations of kind OTHER; payments and queries classify such a response as AMBIGUOUS.
 */
public class InvalidResponseException extends SelcomException {
    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final transient String rawBody;

    /**
     * Creates the exception.
     *
     * @param httpStatus the HTTP status of the response
     * @param rawBody the raw response body
     */
    public InvalidResponseException(int httpStatus, String rawBody) {
        super("response has no usable resultcode (HTTP " + httpStatus + ")");
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    /**
     * The HTTP status of the response.
     *
     * @return the status
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * The raw response body, for audit. Redact it before logging.
     *
     * @return the body, possibly empty
     */
    public String rawBody() {
        return rawBody;
    }
}
