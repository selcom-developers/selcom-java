package io.github.selcomdevelopers.selcom.result;

import java.util.List;
import java.util.Optional;

/**
 * What every operation returns: the typed status, the gateway's code and text, the HTTP status, the decoded data and
 * the raw body.
 *
 * @param <T> the type of one {@code data} entry
 * @param status one of the five typed statuses
 * @param resultCode the normalised {@code resultcode}, or null when the response carried no verdict
 * @param result the gateway's {@code result} text, unchanged; may be null or empty
 * @param message the gateway's {@code message} text, unchanged; may be null
 * @param reference the gateway's reference, when present
 * @param transId the {@code transid} this result is about, when there is one
 * @param orderId the {@code order_id} this result is about, when there is one
 * @param httpStatus the HTTP status code
 * @param data the decoded {@code data} entries; empty when there are none
 * @param raw the raw response body, for audit; redact before logging
 */
public record Result<T>(
        Status status,
        String resultCode,
        String result,
        String message,
        String reference,
        String transId,
        String orderId,
        int httpStatus,
        List<T> data,
        String raw) {

    /** Makes the data list unmodifiable. */
    public Result {
        data = List.copyOf(data);
    }

    /**
     * Whether the status will not change on a later query.
     *
     * @return true for SUCCESS, FAILED and NOT_FOUND
     */
    public boolean isFinal() {
        return status.isFinal();
    }

    /**
     * The first {@code data} entry, which is where operations that return one thing put it.
     *
     * @return the entry, or empty
     */
    public Optional<T> first() {
        return data.isEmpty() ? Optional.empty() : Optional.of(data.get(0));
    }

    /** Leaves out the raw body and the data, which can hold personal data. */
    @Override
    public String toString() {
        return "Result[status=" + status + ", resultCode=" + resultCode + ", reference=" + reference
                + ", transId=" + transId + ", orderId=" + orderId + ", httpStatus=" + httpStatus + "]";
    }
}
