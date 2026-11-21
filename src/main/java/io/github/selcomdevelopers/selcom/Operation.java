package io.github.selcomdevelopers.selcom;

import io.github.selcomdevelopers.selcom.signing.HttpMethod;
import java.util.Arrays;
import java.util.Optional;

/** Every v1 operation: name, HTTP method, path and kind. */
public enum Operation {
    /** Create an order payable by any enabled method, including cards. */
    CHECKOUT_CREATE_ORDER("checkout.create-order", HttpMethod.POST, "/v1/checkout/create-order", OperationKind.OTHER, true),
    /** Create an order for non-card payment. */
    CHECKOUT_CREATE_ORDER_MINIMAL("checkout.create-order-minimal", HttpMethod.POST, "/v1/checkout/create-order-minimal", OperationKind.OTHER, true),
    /** Push a USSD prompt to a wallet. */
    CHECKOUT_WALLET_PAYMENT("checkout.wallet-payment", HttpMethod.POST, "/v1/checkout/wallet-payment", OperationKind.PAYMENT, true),
    /** Charge a stored card. */
    CHECKOUT_CARD_PAYMENT("checkout.card-payment", HttpMethod.POST, "/v1/checkout/card-payment", OperationKind.PAYMENT, true),
    /** Read one order. */
    CHECKOUT_ORDER_STATUS("checkout.order-status", HttpMethod.GET, "/v1/checkout/order-status", OperationKind.ORDER_STATUS, false),
    /** Cancel an unpaid order. */
    CHECKOUT_CANCEL_ORDER("checkout.cancel-order", HttpMethod.DELETE, "/v1/checkout/cancel-order", OperationKind.OTHER, true),
    /** List orders in a date range. */
    CHECKOUT_LIST_ORDERS("checkout.list-orders", HttpMethod.GET, "/v1/checkout/list-orders", OperationKind.OTHER, false),
    /** List a buyer's stored cards. */
    CHECKOUT_STORED_CARDS("checkout.stored-cards", HttpMethod.GET, "/v1/checkout/stored-cards", OperationKind.OTHER, false),
    /** Remove a stored card. */
    CHECKOUT_DELETE_CARD("checkout.delete-card", HttpMethod.DELETE, "/v1/checkout/delete-card", OperationKind.OTHER, true),
    /** Pay a bill or buy a prepaid service from the float. */
    UTILITYPAYMENT_PROCESS("utilitypayment.process", HttpMethod.POST, "/v1/utilitypayment/process", OperationKind.PAYMENT, true),
    /** Look up a customer reference before paying. */
    UTILITYPAYMENT_LOOKUP("utilitypayment.lookup", HttpMethod.GET, "/v1/utilitypayment/lookup", OperationKind.OTHER, false),
    /** Verdict on an earlier utility payment. */
    UTILITYPAYMENT_QUERY("utilitypayment.query", HttpMethod.GET, "/v1/utilitypayment/query", OperationKind.STATUS_QUERY, false),
    /** Credit a mobile wallet from the float. */
    WALLETCASHIN_PROCESS("walletcashin.process", HttpMethod.POST, "/v1/walletcashin/process", OperationKind.PAYMENT, true),
    /** Registered name on a wallet. */
    WALLETCASHIN_NAMELOOKUP("walletcashin.namelookup", HttpMethod.GET, "/v1/walletcashin/namelookup", OperationKind.OTHER, false),
    /** Verdict on an earlier wallet cash-in. */
    WALLETCASHIN_QUERY("walletcashin.query", HttpMethod.GET, "/v1/walletcashin/query", OperationKind.STATUS_QUERY, false),
    /** Float account balance. */
    VENDOR_BALANCE("vendor.balance", HttpMethod.POST, "/v1/vendor/balance", OperationKind.OTHER, false);

    private final String operationName;
    private final HttpMethod method;
    private final String path;
    private final OperationKind kind;
    private final boolean write;

    Operation(String operationName, HttpMethod method, String path, OperationKind kind, boolean write) {
        this.operationName = operationName;
        this.method = method;
        this.path = path;
        this.kind = kind;
        this.write = write;
    }

    /**
     * The operation's name as used in the spec, for example {@code checkout.create-order}.
     *
     * @return the name
     */
    public String operationName() {
        return operationName;
    }

    /**
     * The HTTP method.
     *
     * @return the method
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * The path, starting with {@code /v1/}.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * The kind, which decides classification and timeouts.
     *
     * @return the kind
     */
    public OperationKind kind() {
        return kind;
    }

    /**
     * Whether this operation writes (creates, cancels, deletes or moves money). Such operations are never retried.
     *
     * @return true for writes and payments
     */
    public boolean isWrite() {
        return write || kind == OperationKind.PAYMENT;
    }

    /**
     * Whether this is a checkout operation, which may use a separate credential set.
     *
     * @return true for {@code checkout.*}
     */
    public boolean isCheckout() {
        return path.startsWith("/v1/checkout/");
    }

    /**
     * Finds an operation by its spec name.
     *
     * @param name for example {@code vendor.balance}
     * @return the operation, or empty
     */
    public static Optional<Operation> byName(String name) {
        return Arrays.stream(values()).filter(o -> o.operationName.equals(name)).findFirst();
    }
}
