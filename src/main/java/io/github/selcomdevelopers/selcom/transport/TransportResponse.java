package io.github.selcomdevelopers.selcom.transport;

import java.util.Objects;

/**
 * An HTTP response, whatever its status.
 *
 * @param status the HTTP status code
 * @param body the response body decoded as UTF-8; empty if there was none
 */
public record TransportResponse(int status, String body) {

    /** Normalises a null body to the empty string. */
    public TransportResponse {
        body = Objects.requireNonNullElse(body, "");
    }

    /** Leaves the body out, since it can hold personal data. */
    @Override
    public String toString() {
        return "TransportResponse[status=" + status + ", bodyLength=" + body.length() + "]";
    }
}
