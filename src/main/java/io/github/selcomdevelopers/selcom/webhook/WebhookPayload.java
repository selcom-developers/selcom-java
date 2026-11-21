package io.github.selcomdevelopers.selcom.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.Money;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.result.Classifier;
import io.github.selcomdevelopers.selcom.result.Status;

/**
 * The documented fields of a checkout webhook body. Unknown fields are ignored. Everything here is a hint: confirm with
 * {@code orderStatus} before acting, and take the amount from there too.
 *
 * @param orderId the merchant's order ID
 * @param transId the payment attempt's ID
 * @param reference the gateway's reference for the payment
 * @param result the gateway's {@code result} text
 * @param resultCode the {@code resultcode}, trimmed
 * @param paymentStatus for example {@code COMPLETED}
 * @param channel the payment channel, for example a wallet name
 * @param amount the amount as text; it may contain thousands separators such as {@code "10,000"}
 * @param phone the payer's number (read from {@code phone}, or {@code msisdn})
 */
public record WebhookPayload(String orderId, String transId, String reference, String result, String resultCode,
                             String paymentStatus, String channel, String amount, String phone) {

    /**
     * Parses a webhook body.
     *
     * @param rawBody the body as received
     * @return the payload
     * @throws IllegalArgumentException if the body is not a JSON object
     */
    public static WebhookPayload parse(String rawBody) {
        JsonNode body = Json.parse(rawBody);
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("webhook body is not a JSON object");
        }
        String code = Classifier.resultCode(body);
        String phone = Json.text(body, "phone");
        return new WebhookPayload(Json.text(body, "order_id"), Json.text(body, "transid"), Json.text(body, "reference"),
                Json.text(body, "result"), code, Json.text(body, "payment_status"), Json.text(body, "channel"),
                Json.text(body, "amount"), phone != null ? phone : Json.text(body, "msisdn"));
    }

    /**
     * The delivery's own claim about the payment, from {@code payment_status}. A hint only.
     *
     * @return the typed status
     */
    public Status claimedStatus() {
        return Classifier.fromPaymentStatus(paymentStatus);
    }

    /**
     * The amount in whole shillings, strictly parsed from text such as {@code "10000"} or {@code "10,000"}.
     * This amount is usually not covered by the signature.
     *
     * @return the amount in TZS
     * @throws IllegalArgumentException if the amount is missing or not a whole number
     */
    public Money amountTzs() {
        return Money.parseTzs(amount);
    }

    /** Leaves out the payer's number. */
    @Override
    public String toString() {
        return "WebhookPayload[orderId=" + orderId + ", transId=" + transId + ", resultCode=" + resultCode
                + ", paymentStatus=" + paymentStatus + "]";
    }
}
