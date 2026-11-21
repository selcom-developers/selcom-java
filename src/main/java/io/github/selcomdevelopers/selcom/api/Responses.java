package io.github.selcomdevelopers.selcom.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.Currency;
import io.github.selcomdevelopers.selcom.Money;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.result.Classifier;
import io.github.selcomdevelopers.selcom.result.Status;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The typed {@code data} entries of responses. Each record is read leniently from one entry: unknown fields are ignored and
 * missing or null fields are null. {@code toString()} of every record leaves out personal data and card tokens.
 */
public final class Responses {
    private Responses() {
    }

    /**
     * Reads the {@code data} array of a response body into typed entries.
     *
     * @param body the decoded body, or null
     * @param reader reads one entry, an object
     * @param <T> the entry type
     * @return the entries, in order; empty if {@code data} is missing or not an array; non-object entries are skipped
     */
    public static <T> List<T> entries(JsonNode body, Function<JsonNode, T> reader) {
        List<T> out = new ArrayList<>();
        if (body != null && body.isObject() && body.get("data") != null && body.get("data").isArray()) {
            for (JsonNode entry : body.get("data")) {
                if (entry.isObject()) {
                    out.add(reader.apply(entry));
                }
            }
        }
        return out;
    }

    /**
     * Reads one entry as an untyped, ordered map. Used by operations whose {@code data} is free-form.
     *
     * @param entry one {@code data} entry
     * @return an unmodifiable map
     */
    public static Map<String, Object> asMap(JsonNode entry) {
        Map<String, Object> map = Json.mapper().convertValue(entry, new TypeReference<LinkedHashMap<String, Object>>() { });
        return Collections.unmodifiableMap(map);
    }

    /**
     * The result of creating an order.
     *
     * @param gatewayBuyerUuid the buyer's gateway UUID; keep it with the buyer and send it on their later orders
     * @param paymentToken a code the buyer can pay with
     * @param qr the QR payload
     * @param paymentGatewayUrlRaw the hosted payment page URL exactly as sent: base64
     * @param paymentGatewayUrl the hosted payment page URL decoded, or null if the raw value is not valid base64
     */
    public record OrderCreated(String gatewayBuyerUuid, String paymentToken, String qr, String paymentGatewayUrlRaw,
                               String paymentGatewayUrl) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static OrderCreated from(JsonNode n) {
            String raw = Json.text(n, "payment_gateway_url");
            return new OrderCreated(Json.text(n, "gateway_buyer_uuid"), Json.text(n, "payment_token"), Json.text(n, "qr"),
                    raw, decodeBase64(raw));
        }

        @Override public String toString() {
            return "OrderCreated[hasPaymentUrl=" + (paymentGatewayUrl != null) + "]";
        }
    }

    /**
     * One order as returned by order-status.
     *
     * @param orderId the order ID
     * @param creationDate when it was created, as text
     * @param amount the amount as text, possibly with thousands separators
     * @param paymentStatus the gateway's {@code payment_status}, for example {@code COMPLETED}
     * @param transId the payment's {@code transid}; filled once the order is paid
     * @param channel the payment channel, for example a wallet name
     * @param reference the gateway's reference
     * @param phone the payer's number (read from {@code msisdn} or {@code phone})
     */
    public record OrderStatusData(String orderId, String creationDate, String amount, String paymentStatus, String transId,
                                  String channel, String reference, String phone) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static OrderStatusData from(JsonNode n) {
            String phone = Json.text(n, "msisdn");
            return new OrderStatusData(Json.text(n, "order_id"), Json.text(n, "creation_date"), Json.text(n, "amount"),
                    Json.text(n, "payment_status"), Json.text(n, "transid"), Json.text(n, "channel"),
                    Json.text(n, "reference"), phone != null ? phone : Json.text(n, "phone"));
        }

        /**
         * The verdict on the order, from {@code payment_status}.
         *
         * @return the typed status
         */
        public Status status() {
            return Classifier.fromPaymentStatus(paymentStatus);
        }

        /**
         * The amount, strictly parsed.
         *
         * @param currency the order's currency
         * @return the money
         * @throws IllegalArgumentException if the amount is missing or not a whole number
         */
        public Money amount(Currency currency) {
            return Money.parse(amount, currency);
        }

        @Override public String toString() {
            return "OrderStatusData[orderId=" + orderId + ", paymentStatus=" + paymentStatus + ", transId=" + transId + "]";
        }
    }

    /**
     * One order in a list.
     *
     * @param orderId the order ID
     * @param creationDate when it was created, as text
     * @param amount the amount as text
     * @param paymentStatus the gateway's {@code payment_status}
     */
    public record OrderSummary(String orderId, String creationDate, String amount, String paymentStatus) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static OrderSummary from(JsonNode n) {
            return new OrderSummary(Json.text(n, "order_id"), Json.text(n, "creation_date"), Json.text(n, "amount"),
                    Json.text(n, "payment_status"));
        }

        /**
         * The verdict on the order, from {@code payment_status}.
         *
         * @return the typed status
         */
        public Status status() {
            return Classifier.fromPaymentStatus(paymentStatus);
        }
    }

    /**
     * A stored card.
     *
     * @param id the card's resource ID, used to delete it
     * @param maskedCard the masked card number
     * @param creationDate when it was stored
     * @param name the cardholder
     * @param cardType {@code 001} Visa, {@code 002} Mastercard
     * @param cardToken the token used to charge the card; sensitive, never log it
     */
    public record StoredCard(String id, String maskedCard, String creationDate, String name, String cardType, String cardToken) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static StoredCard from(JsonNode n) {
            return new StoredCard(Json.text(n, "id"), Json.text(n, "masked_card"), Json.text(n, "creation_date"),
                    Json.text(n, "name"), Json.text(n, "card_type"), Json.text(n, "card_token"));
        }

        @Override public String toString() {
            return "StoredCard[id=" + id + ", maskedCard=" + maskedCard + ", cardType=" + cardType + "]";
        }
    }

    /**
     * The result of a lookup, of a utility reference or of a wallet name.
     *
     * @param name who the reference belongs to
     * @param amount the amount due, for bills; may be null
     * @param institution the institution, for bills; may be null
     * @param type the type, for bills; may be null
     * @param desc a description, for bills; may be null
     */
    public record Lookup(String name, String amount, String institution, String type, String desc) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static Lookup from(JsonNode n) {
            return new Lookup(Json.text(n, "name"), Json.text(n, "amount"), Json.text(n, "institution"),
                    Json.text(n, "type"), Json.text(n, "desc"));
        }

        @Override public String toString() {
            return "Lookup[institution=" + institution + ", type=" + type + "]";
        }
    }

    /**
     * The data of a payout status query.
     *
     * @param receipt the receipt, present for successful payments; may be null
     * @param fields every field of the entry, in order
     */
    public record QueryData(String receipt, Map<String, Object> fields) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static QueryData from(JsonNode n) {
            return new QueryData(Json.text(n, "receipt"), asMap(n));
        }

        @Override public String toString() {
            return "QueryData[hasReceipt=" + (receipt != null) + "]";
        }
    }

    /**
     * The float account balance.
     *
     * @param balance the balance as text, whole shillings
     */
    public record Balance(String balance) {
        /**
         * Reads one entry.
         *
         * @param n the entry
         * @return the record
         */
        public static Balance from(JsonNode n) {
            return new Balance(Json.text(n, "balance"));
        }

        /**
         * The balance, strictly parsed.
         *
         * @return the balance in TZS
         * @throws IllegalArgumentException if it is missing or not a whole number
         */
        public Money amount() {
            return Money.parseTzs(balance);
        }
    }

    private static String decodeBase64(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(raw.strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
