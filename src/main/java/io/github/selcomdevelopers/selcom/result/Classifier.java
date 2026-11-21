package io.github.selcomdevelopers.selcom.result;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.OperationKind;
import java.util.Locale;
import java.util.Map;

/**
 * The verdict mapping from the spec's {@code docs/results.md}, as pure functions of the operation kind and the decoded
 * response body.
 */
public final class Classifier {
    private static final Map<String, Status> PAYMENT_STATUS = Map.ofEntries(
            Map.entry("COMPLETED", Status.SUCCESS),
            Map.entry("COMPLETE", Status.SUCCESS),
            Map.entry("PENDING", Status.PENDING),
            Map.entry("INPROGRESS", Status.PENDING),
            Map.entry("CANCELLED", Status.FAILED),
            Map.entry("CANCELED", Status.FAILED),
            Map.entry("CANCEL", Status.FAILED),
            Map.entry("USERCANCELLED", Status.FAILED),
            Map.entry("USERCANCELED", Status.FAILED),
            Map.entry("REJECTED", Status.FAILED),
            Map.entry("FAILED", Status.FAILED));

    private Classifier() {
    }

    /**
     * Normalises the {@code resultcode} of a body: trimmed, and a JSON number formatted as three digits.
     *
     * @param body the decoded body, or null
     * @return the code, or null if the body is not an object or has no usable code
     */
    public static String resultCode(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode code = body.get("resultcode");
        if (code == null) {
            return null;
        }
        if (code.isTextual()) {
            String trimmed = code.asText().strip();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (code.isIntegralNumber() && code.canConvertToInt() && code.intValue() >= 0) {
            return String.format(Locale.ROOT, "%03d", code.intValue());
        }
        return null;
    }

    /**
     * Classifies a response. Classification looks at {@code resultcode} only; the HTTP status and the {@code result}
     * and {@code message} texts do not decide the verdict. An operation of kind OTHER whose response carries no
     * verdict raises instead.
     *
     * @param kind the operation's kind
     * @param body the decoded body, or null if it was not JSON
     * @param httpStatus the HTTP status, for the error
     * @param rawBody the raw body, for the error
     * @return the typed status
     * @throws InvalidResponseException if the kind is OTHER and the response has no usable {@code resultcode}
     */
    public static Status classify(OperationKind kind, JsonNode body, int httpStatus, String rawBody) {
        String code = resultCode(body);
        if (code == null) {
            // No verdict. For money-moving and query kinds something answered but said nothing, so the money may have
            // moved: AMBIGUOUS. For every other kind the caller gets an error.
            if (kind == OperationKind.OTHER) {
                throw new InvalidResponseException(httpStatus, rawBody);
            }
            return Status.AMBIGUOUS;
        }
        return fromCode(kind, code, body);
    }

    private static Status fromCode(OperationKind kind, String code, JsonNode body) {
        switch (code) {
            case "000":
                return kind == OperationKind.ORDER_STATUS ? classifyPaymentStatus(body) : Status.SUCCESS;
            case "111":
            case "927":
                return Status.PENDING;
            case "999":
                return Status.AMBIGUOUS;
            default:
                break;
        }
        if (code.equals("404") && (kind == OperationKind.STATUS_QUERY || kind == OperationKind.ORDER_STATUS)) {
            return Status.NOT_FOUND;
        }
        // For order-status the query itself failed, which says nothing about the order.
        return kind == OperationKind.ORDER_STATUS ? Status.AMBIGUOUS : Status.FAILED;
    }

    /**
     * Maps {@code data[0].payment_status} of an order-status response. An empty {@code data} array means the order does
     * not exist; a missing or non-array {@code data} is ambiguous.
     *
     * @param body the decoded body
     * @return the typed status
     */
    public static Status classifyPaymentStatus(JsonNode body) {
        JsonNode data = body.get("data");
        if (data == null || !data.isArray()) {
            return Status.AMBIGUOUS;
        }
        if (data.isEmpty()) {
            return Status.NOT_FOUND;
        }
        return fromPaymentStatus(data.get(0) == null ? null : data.get(0).get("payment_status"));
    }

    /**
     * Maps a {@code payment_status} value, compared case-insensitively after trimming.
     *
     * @param paymentStatus the JSON value, or null
     * @return the typed status; AMBIGUOUS for anything unknown or missing
     */
    public static Status fromPaymentStatus(JsonNode paymentStatus) {
        if (paymentStatus == null || !paymentStatus.isTextual()) {
            return Status.AMBIGUOUS;
        }
        return fromPaymentStatus(paymentStatus.asText());
    }

    /**
     * Maps a {@code payment_status} text, compared case-insensitively after trimming.
     *
     * @param paymentStatus the text, or null
     * @return the typed status; AMBIGUOUS for anything unknown or missing
     */
    public static Status fromPaymentStatus(String paymentStatus) {
        if (paymentStatus == null) {
            return Status.AMBIGUOUS;
        }
        return PAYMENT_STATUS.getOrDefault(paymentStatus.strip().toUpperCase(Locale.ROOT), Status.AMBIGUOUS);
    }
}
