package io.github.selcomdevelopers.selcom.api;

import io.github.selcomdevelopers.selcom.Currency;
import io.github.selcomdevelopers.selcom.Money;
import io.github.selcomdevelopers.selcom.Operation;
import io.github.selcomdevelopers.selcom.redact.Redactor;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The simple typed requests. Each is an immutable record that validates itself and produces its parameters in the order
 * the spec lists them. Order creation has its own builders: {@link CreateOrderRequest} and
 * {@link CreateOrderMinimalRequest}.
 *
 * <p>The vendor and the float PIN are never part of a request; they come from the client's configuration.
 * {@code toString()} of every request redacts personal data and secrets.
 */
public final class Requests {
    private Requests() {
    }

    static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    static Money requirePositive(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.units() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return amount;
    }

    static Money requireTzs(Money amount) {
        requirePositive(amount);
        if (amount.currency() != Currency.TZS) {
            throw new IllegalArgumentException("payouts are TZS only");
        }
        return amount;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }

    private static String describe(ApiRequest request) {
        Redactor redactor = request.operation().path().startsWith("/v1/walletcashin/")
                ? Redactor.treatingUtilityRefAsPhone() : Redactor.defaults();
        Map<String, Object> params = request.params(new Defaults("-", "-"));
        return request.getClass().getSimpleName() + redactor.redactMap(params);
    }

    /**
     * {@code checkout.order-status}: the state of one order.
     *
     * @param orderId the merchant's order ID
     */
    public record OrderStatus(String orderId) implements ApiRequest {
        /** Validates the ID. */
        public OrderStatus {
            require(orderId, "orderId");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_ORDER_STATUS;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("order_id", orderId);
        }

        @Override public String orderId() {
            return orderId;
        }
    }

    /**
     * {@code checkout.cancel-order}: cancels an unpaid order. A successful cancel proves the order was never paid.
     *
     * @param orderId the merchant's order ID
     */
    public record CancelOrder(String orderId) implements ApiRequest {
        /** Validates the ID. */
        public CancelOrder {
            require(orderId, "orderId");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_CANCEL_ORDER;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("order_id", orderId);
        }

        @Override public String orderId() {
            return orderId;
        }
    }

    /**
     * {@code checkout.list-orders}: the vendor's orders created in a date range.
     *
     * @param from first day
     * @param to last day
     */
    public record ListOrders(LocalDate from, LocalDate to) implements ApiRequest {
        /** Validates the range. */
        public ListOrders {
            if (from == null || to == null) {
                throw new IllegalArgumentException("from and to are required");
            }
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("to is before from");
            }
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_LIST_ORDERS;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("fromdate", from.toString(), "todate", to.toString());
        }
    }

    /**
     * {@code checkout.stored-cards}: the cards stored for a buyer.
     *
     * @param gatewayBuyerUuid the buyer's gateway UUID, from their first order
     * @param buyerUserId the buyer's ID, as on their orders
     */
    public record StoredCards(String gatewayBuyerUuid, String buyerUserId) implements ApiRequest {
        /** Validates the IDs. */
        public StoredCards {
            require(gatewayBuyerUuid, "gatewayBuyerUuid");
            require(buyerUserId, "buyerUserId");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_STORED_CARDS;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("gateway_buyer_uuid", gatewayBuyerUuid, "buyer_userid", buyerUserId);
        }
    }

    /**
     * {@code checkout.delete-card}: removes a stored card.
     *
     * @param cardId the card's resource ID from stored-cards
     * @param gatewayBuyerUuid the buyer's gateway UUID
     */
    public record DeleteCard(String cardId, String gatewayBuyerUuid) implements ApiRequest {
        /** Validates the IDs. */
        public DeleteCard {
            require(cardId, "cardId");
            require(gatewayBuyerUuid, "gatewayBuyerUuid");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_DELETE_CARD;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("id", cardId, "gateway_buyer_uuid", gatewayBuyerUuid);
        }
    }

    /**
     * {@code checkout.wallet-payment}: pushes a USSD prompt to the buyer's wallet. Money-moving: never retried.
     *
     * @param transId the merchant's unique ID for this attempt
     * @param orderId an order created with create-order-minimal or create-order
     * @param msisdn the wallet to charge, {@code 255...}
     */
    public record WalletPayment(String transId, String orderId, String msisdn) implements ApiRequest {
        /** Validates the fields. */
        public WalletPayment {
            require(transId, "transId");
            require(orderId, "orderId");
            require(msisdn, "msisdn");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_WALLET_PAYMENT;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId, "order_id", orderId, "msisdn", msisdn);
        }

        @Override public String transId() {
            return transId;
        }

        @Override public String orderId() {
            return orderId;
        }

        @Override public String toString() {
            return describe(this);
        }
    }

    /**
     * {@code checkout.card-payment}: charges a stored card for an existing order. Money-moving: never retried.
     *
     * @param transId the merchant's unique ID for this attempt
     * @param orderId the order
     * @param cardToken the token from stored-cards
     * @param buyerUserId the buyer's ID, as on their orders
     * @param gatewayBuyerUuid the buyer's gateway UUID
     */
    public record CardPayment(String transId, String orderId, String cardToken, String buyerUserId, String gatewayBuyerUuid)
            implements ApiRequest {
        /** Validates the fields. */
        public CardPayment {
            require(transId, "transId");
            require(orderId, "orderId");
            require(cardToken, "cardToken");
            require(buyerUserId, "buyerUserId");
            require(gatewayBuyerUuid, "gatewayBuyerUuid");
        }

        @Override public Operation operation() {
            return Operation.CHECKOUT_CARD_PAYMENT;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId, "vendor", defaults.vendor(), "order_id", orderId, "card_token", cardToken,
                    "buyer_userid", buyerUserId, "gateway_buyer_uuid", gatewayBuyerUuid);
        }

        @Override public String transId() {
            return transId;
        }

        @Override public String orderId() {
            return orderId;
        }

        @Override public String toString() {
            return describe(this);
        }
    }

    /**
     * {@code utilitypayment.process}: pays a bill or buys a prepaid service from the float. Money-moving: never retried.
     *
     * @param transId the merchant's unique ID; also the key for the status query
     * @param utilityCode which service, for example {@code LUKU} or {@code TOP}
     * @param utilityRef the customer's reference at that service
     * @param amount whole TZS
     * @param msisdn the end customer's phone number, or null
     */
    public record UtilityPayment(String transId, String utilityCode, String utilityRef, Money amount, String msisdn)
            implements ApiRequest {
        /** Validates the fields. */
        public UtilityPayment {
            require(transId, "transId");
            require(utilityCode, "utilityCode");
            require(utilityRef, "utilityRef");
            requireTzs(amount);
        }

        @Override public Operation operation() {
            return Operation.UTILITYPAYMENT_PROCESS;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId, "utilitycode", utilityCode, "utilityref", utilityRef, "amount", amount.units(),
                    "vendor", defaults.vendor(), "pin", defaults.requirePin(), "msisdn", msisdn);
        }

        @Override public String transId() {
            return transId;
        }

        @Override public String toString() {
            return describe(this);
        }
    }

    /**
     * {@code utilitypayment.lookup}: checks a customer reference before paying.
     *
     * @param utilityCode the service
     * @param utilityRef the customer's reference
     * @param transId a unique ID; the same one may be used for the payment that follows
     */
    public record UtilityLookup(String utilityCode, String utilityRef, String transId) implements ApiRequest {
        /** Validates the fields. */
        public UtilityLookup {
            require(utilityCode, "utilityCode");
            require(utilityRef, "utilityRef");
            require(transId, "transId");
        }

        @Override public Operation operation() {
            return Operation.UTILITYPAYMENT_LOOKUP;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("utilitycode", utilityCode, "utilityref", utilityRef, "transid", transId);
        }

        @Override public String transId() {
            return transId;
        }
    }

    /**
     * {@code utilitypayment.query}: the verdict on an earlier utility payment.
     *
     * @param transId the payment's {@code transid}
     */
    public record UtilityQuery(String transId) implements ApiRequest {
        /** Validates the ID. */
        public UtilityQuery {
            require(transId, "transId");
        }

        @Override public Operation operation() {
            return Operation.UTILITYPAYMENT_QUERY;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId);
        }

        @Override public String transId() {
            return transId;
        }
    }

    /**
     * {@code walletcashin.process}: credits a mobile wallet from the float. Money-moving: never retried.
     *
     * @param transId the merchant's unique ID
     * @param utilityCode the destination wallet, for example {@code VMCASHIN}, or {@code CASHIN} to route by network
     * @param utilityRef the receiving wallet's phone number
     * @param amount whole TZS
     * @param msisdn the sender's or initiator's phone number, or null
     */
    public record WalletCashIn(String transId, String utilityCode, String utilityRef, Money amount, String msisdn)
            implements ApiRequest {
        /** Validates the fields. */
        public WalletCashIn {
            require(transId, "transId");
            require(utilityCode, "utilityCode");
            require(utilityRef, "utilityRef");
            requireTzs(amount);
        }

        @Override public Operation operation() {
            return Operation.WALLETCASHIN_PROCESS;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId, "utilitycode", utilityCode, "utilityref", utilityRef, "amount", amount.units(),
                    "vendor", defaults.vendor(), "pin", defaults.requirePin(), "msisdn", msisdn);
        }

        @Override public String transId() {
            return transId;
        }

        @Override public String toString() {
            return describe(this);
        }
    }

    /**
     * {@code walletcashin.namelookup}: the registered name on a wallet.
     *
     * @param utilityCode the wallet code
     * @param utilityRef the wallet's phone number
     * @param transId a unique ID; may be reused for the cash-in that follows
     */
    public record WalletNameLookup(String utilityCode, String utilityRef, String transId) implements ApiRequest {
        /** Validates the fields. */
        public WalletNameLookup {
            require(utilityCode, "utilityCode");
            require(utilityRef, "utilityRef");
            require(transId, "transId");
        }

        @Override public Operation operation() {
            return Operation.WALLETCASHIN_NAMELOOKUP;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("utilitycode", utilityCode, "utilityref", utilityRef, "transid", transId);
        }

        @Override public String transId() {
            return transId;
        }

        @Override public String toString() {
            return describe(this);
        }
    }

    /**
     * {@code walletcashin.query}: the verdict on an earlier wallet cash-in.
     *
     * @param transId the cash-in's {@code transid}
     */
    public record WalletCashInQuery(String transId) implements ApiRequest {
        /** Validates the ID. */
        public WalletCashInQuery {
            require(transId, "transId");
        }

        @Override public Operation operation() {
            return Operation.WALLETCASHIN_QUERY;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("transid", transId);
        }

        @Override public String transId() {
            return transId;
        }
    }

    /**
     * {@code vendor.balance}: the float account balance. A POST because it carries the PIN; it moves no money.
     *
     * @param transId a unique ID
     */
    public record VendorBalance(String transId) implements ApiRequest {
        /** Validates the ID. */
        public VendorBalance {
            require(transId, "transId");
        }

        @Override public Operation operation() {
            return Operation.VENDOR_BALANCE;
        }

        @Override public Map<String, Object> params(Defaults defaults) {
            return map("vendor", defaults.vendor(), "pin", defaults.requirePin(), "transid", transId);
        }

        @Override public String transId() {
            return transId;
        }
    }
}
