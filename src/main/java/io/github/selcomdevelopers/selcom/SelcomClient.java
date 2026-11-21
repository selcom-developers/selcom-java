package io.github.selcomdevelopers.selcom;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.api.ApiRequest;
import io.github.selcomdevelopers.selcom.api.CreateOrderMinimalRequest;
import io.github.selcomdevelopers.selcom.api.CreateOrderRequest;
import io.github.selcomdevelopers.selcom.api.Defaults;
import io.github.selcomdevelopers.selcom.api.Requests;
import io.github.selcomdevelopers.selcom.api.Responses;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.result.Classifier;
import io.github.selcomdevelopers.selcom.result.Result;
import io.github.selcomdevelopers.selcom.result.Status;
import io.github.selcomdevelopers.selcom.signing.Hs256Signer;
import io.github.selcomdevelopers.selcom.signing.RequestSigner;
import io.github.selcomdevelopers.selcom.signing.SignedRequest;
import io.github.selcomdevelopers.selcom.signing.Signer;
import io.github.selcomdevelopers.selcom.signing.Timestamps;
import io.github.selcomdevelopers.selcom.transport.JdkHttpTransport;
import io.github.selcomdevelopers.selcom.transport.Transport;
import io.github.selcomdevelopers.selcom.transport.TransportException;
import io.github.selcomdevelopers.selcom.transport.TransportRequest;
import io.github.selcomdevelopers.selcom.transport.TransportResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/**
 * The client: one typed method per v1 operation, wiring the signer, the transport and the classifier together.
 *
 * <p>The client is stateless and thread-safe. It does not lock, queue, deduplicate or retry: a payment or write is sent
 * once, and when no response arrives you get a {@link TransportException} saying whether the request was
 * {@link TransportException.Outcome#NOT_SENT never sent} or has an
 * {@link TransportException.Outcome#UNKNOWN_OUTCOME unknown outcome}. Submitting and querying are separate methods;
 * nothing hides a query inside a submit.
 *
 * <p>Every method returns a {@link Result} with one of five {@link Status statuses}. Signing errors are raised before
 * anything is sent.
 */
public final class SelcomClient {
    /** The version of this library, sent in the {@code User-Agent} header. */
    public static final String VERSION = loadVersion();

    private final SelcomConfig config;
    private final Transport transport;
    private final Clock clock;
    private final Signer signer;
    private final Signer checkoutSigner;
    private final Defaults defaults;

    private SelcomClient(Builder b) {
        this.config = b.config;
        this.transport = b.transport != null ? b.transport : new JdkHttpTransport();
        this.clock = b.clock;
        this.signer = b.signer != null ? b.signer : new Hs256Signer(config.credentials().secret());
        this.checkoutSigner = b.checkoutSigner != null ? b.checkoutSigner : new Hs256Signer(config.checkoutCredentials().secret());
        this.defaults = new Defaults(config.vendor(), config.pin());
    }

    /**
     * Creates a client with the default transport, the system clock and HS256 signing.
     *
     * @param config the configuration
     * @return the client
     */
    public static SelcomClient create(SelcomConfig config) {
        return builder(config).build();
    }

    /**
     * Starts a builder, to replace the transport, the clock or the signer.
     *
     * @param config the configuration
     * @return a new builder
     */
    public static Builder builder(SelcomConfig config) {
        return new Builder(config);
    }

    /**
     * The configuration this client was built with.
     *
     * @return the configuration
     */
    public SelcomConfig config() {
        return config;
    }

    // ---- Checkout ----

    /**
     * {@code checkout.create-order}: creates an order payable by any enabled method, including cards. Not retried.
     *
     * @param request the order
     * @return the result; {@code data} holds the payment links
     */
    public Result<Responses.OrderCreated> createOrder(CreateOrderRequest request) {
        return execute(request, Responses.OrderCreated::from);
    }

    /**
     * {@code checkout.create-order-minimal}: creates an order for non-card payment. Not retried.
     *
     * @param request the order
     * @return the result; {@code data} holds the payment links
     */
    public Result<Responses.OrderCreated> createOrderMinimal(CreateOrderMinimalRequest request) {
        return execute(request, Responses.OrderCreated::from);
    }

    /**
     * {@code checkout.wallet-payment}: pushes a USSD prompt to the buyer's wallet. Money-moving: never retried. Normally
     * returns PENDING; the outcome arrives by webhook and is confirmed with {@link #orderStatus}.
     *
     * @param request the payment
     * @return the result
     */
    public Result<Map<String, Object>> walletPayment(Requests.WalletPayment request) {
        return execute(request);
    }

    /**
     * {@code checkout.card-payment}: charges a stored card. Money-moving: never retried.
     *
     * @param request the payment
     * @return the result
     */
    public Result<Map<String, Object>> cardPayment(Requests.CardPayment request) {
        return execute(request);
    }

    /**
     * {@code checkout.order-status}: the state of one order. This, not a webhook, is the source of truth for checkout.
     * The top-level result code says only whether the query worked; the verdict is {@code data[0].payment_status}.
     *
     * @param request the order to read
     * @return the result
     */
    public Result<Responses.OrderStatusData> orderStatus(Requests.OrderStatus request) {
        return execute(request, Responses.OrderStatusData::from);
    }

    /**
     * {@code checkout.cancel-order}: cancels an unpaid order. A successful cancel proves the order was never paid.
     *
     * @param request the order to cancel
     * @return the result
     */
    public Result<Map<String, Object>> cancelOrder(Requests.CancelOrder request) {
        return execute(request);
    }

    /**
     * {@code checkout.list-orders}: the vendor's orders created in a date range, for reconciliation.
     *
     * @param request the date range
     * @return the result; one {@code data} entry per order
     */
    public Result<Responses.OrderSummary> listOrders(Requests.ListOrders request) {
        return execute(request, Responses.OrderSummary::from);
    }

    /**
     * {@code checkout.stored-cards}: the cards stored for a buyer.
     *
     * @param request the buyer
     * @return the result; one {@code data} entry per card
     */
    public Result<Responses.StoredCard> storedCards(Requests.StoredCards request) {
        return execute(request, Responses.StoredCard::from);
    }

    /**
     * {@code checkout.delete-card}: removes a stored card. Not retried.
     *
     * @param request the card
     * @return the result
     */
    public Result<Map<String, Object>> deleteCard(Requests.DeleteCard request) {
        return execute(request);
    }

    // ---- Payouts ----

    /**
     * {@code utilitypayment.process}: pays a bill or buys a prepaid service from the float. Money-moving: never retried.
     * Settle PENDING and AMBIGUOUS with {@link #utilityQuery} after 3 minutes or more.
     *
     * @param request the payment
     * @return the result
     */
    public Result<Map<String, Object>> utilityPayment(Requests.UtilityPayment request) {
        return execute(request);
    }

    /**
     * {@code utilitypayment.lookup}: checks a customer reference before paying.
     *
     * @param request the lookup
     * @return the result
     */
    public Result<Responses.Lookup> utilityLookup(Requests.UtilityLookup request) {
        return execute(request, Responses.Lookup::from);
    }

    /**
     * {@code utilitypayment.query}: the verdict on an earlier utility payment. NOT_FOUND means the gateway never recorded
     * the {@code transid}.
     *
     * @param request the payment to query
     * @return the result
     */
    public Result<Responses.QueryData> utilityQuery(Requests.UtilityQuery request) {
        return execute(request, Responses.QueryData::from);
    }

    /**
     * {@code walletcashin.process}: credits a mobile wallet from the float. Money-moving: never retried. Settle
     * non-final results with {@link #walletCashInQuery}.
     *
     * @param request the cash-in
     * @return the result
     */
    public Result<Map<String, Object>> walletCashIn(Requests.WalletCashIn request) {
        return execute(request);
    }

    /**
     * {@code walletcashin.namelookup}: the registered name on a wallet, before sending money to it.
     *
     * @param request the lookup
     * @return the result
     */
    public Result<Responses.Lookup> walletNameLookup(Requests.WalletNameLookup request) {
        return execute(request, Responses.Lookup::from);
    }

    /**
     * {@code walletcashin.query}: the verdict on an earlier wallet cash-in. NOT_FOUND means the gateway never recorded the
     * {@code transid}.
     *
     * @param request the cash-in to query
     * @return the result
     */
    public Result<Responses.QueryData> walletCashInQuery(Requests.WalletCashInQuery request) {
        return execute(request, Responses.QueryData::from);
    }

    /**
     * {@code vendor.balance}: the float account balance. Moves no money.
     *
     * @param request the request
     * @return the result; {@code data[0].balance}
     */
    public Result<Responses.Balance> vendorBalance(Requests.VendorBalance request) {
        return execute(request, Responses.Balance::from);
    }

    // ---- Lower layers ----

    /**
     * Sends any {@link ApiRequest} and returns its {@code data} entries as untyped maps.
     *
     * @param request the request
     * @return the result
     */
    public Result<Map<String, Object>> execute(ApiRequest request) {
        return execute(request, Responses::asMap);
    }

    /**
     * Sends any {@link ApiRequest} and reads its {@code data} entries with your reader.
     *
     * @param request the request
     * @param reader reads one {@code data} entry
     * @param <T> the entry type
     * @return the result
     */
    public <T> Result<T> execute(ApiRequest request, Function<JsonNode, T> reader) {
        Objects.requireNonNull(request, "request");
        return run(request.operation(), request.params(defaults), request.transId(), request.orderId(), reader);
    }

    /**
     * Sends an operation with parameters you built yourself: signed, classified and returned like any other. Vendor and PIN
     * are not added; put them in the parameters if the operation needs them.
     *
     * @param operation the operation
     * @param params the parameters in send order
     * @return the result, with {@code data} entries as untyped maps
     */
    public Result<Map<String, Object>> execute(Operation operation, Map<String, ?> params) {
        Map<String, Object> copy = new LinkedHashMap<>(params);
        Object transId = copy.get("transid");
        Object orderId = copy.get("order_id");
        return run(operation, copy, transId instanceof String s ? s : null, orderId instanceof String s ? s : null, Responses::asMap);
    }

    private <T> Result<T> run(Operation operation, Map<String, Object> params, String requestTransId, String requestOrderId,
                              Function<JsonNode, T> reader) {
        boolean checkout = operation.isCheckout();
        Credentials credentials = checkout ? config.checkoutCredentials() : config.credentials();
        Signer activeSigner = checkout ? checkoutSigner : signer;

        // Sign first. A signing error leaves here before anything is built or sent, and is never caught.
        String timestamp = Timestamps.now(clock, config.zone());
        SignedRequest signed = RequestSigner.sign(operation.method(), params, timestamp, credentials.apiKey(), activeSigner);

        URI uri = URI.create(config.baseUrl() + operation.path() + (signed.query() == null ? "" : "?" + signed.query()));
        Map<String, String> headers = new LinkedHashMap<>(signed.headers());
        headers.put("Accept", "application/json");
        if (signed.body() != null) {
            headers.put("Content-Type", "application/json");
        }
        headers.put("User-Agent", "selcom-java/" + VERSION);
        TransportRequest request = new TransportRequest(operation.method(), uri, headers, signed.body(),
                config.timeoutsFor(operation.kind()));

        TransportResponse response = send(request);

        JsonNode parsed = Json.parse(response.body());
        JsonNode body = parsed != null && parsed.isObject() ? parsed : null;
        Status status = Classifier.classify(operation.kind(), body, response.status(), response.body());
        String transId = Json.text(body, "transid");
        String orderId = Json.text(body, "order_id");
        List<T> data = Responses.entries(body, reader);
        return new Result<>(status, Classifier.resultCode(body), Json.text(body, "result"), Json.text(body, "message"),
                Json.text(body, "reference"), transId != null ? transId : requestTransId,
                orderId != null ? orderId : requestOrderId, response.status(), data, response.body());
    }

    private TransportResponse send(TransportRequest request) {
        try {
            TransportResponse response = transport.send(request);
            if (response == null) {
                throw new TransportException(TransportException.Outcome.UNKNOWN_OUTCOME, "the transport returned no response", null);
            }
            return response;
        } catch (TransportException e) {
            throw e;
        } catch (RuntimeException e) {
            // A custom transport failed in a way it did not classify. The request may have been written.
            throw new TransportException(TransportException.Outcome.UNKNOWN_OUTCOME,
                    "the transport failed with " + e.getClass().getSimpleName(), e);
        }
    }

    private static String loadVersion() {
        try (InputStream in = SelcomClient.class.getResourceAsStream("/selcom-client.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "unknown";
    }

    /** Builds a {@link SelcomClient}. */
    public static final class Builder {
        private final SelcomConfig config;
        private Transport transport;
        private Clock clock = Clock.systemUTC();
        private Signer signer;
        private Signer checkoutSigner;

        private Builder(SelcomConfig config) {
            this.config = Objects.requireNonNull(config, "config");
        }

        /**
         * Replaces the transport: your own HTTP client, a proxy, logging, or a test fake.
         *
         * @param value the transport
         * @return this builder
         */
        public Builder transport(Transport value) {
            this.transport = Objects.requireNonNull(value, "transport");
            return this;
        }

        /**
         * Replaces the clock the timestamps come from. Tests fix it.
         *
         * @param value the clock
         * @return this builder
         */
        public Builder clock(Clock value) {
            this.clock = Objects.requireNonNull(value, "clock");
            return this;
        }

        /**
         * Replaces the digest algorithm for payouts (and for checkout unless {@link #checkoutSigner} is set).
         *
         * @param value the signer
         * @return this builder
         */
        public Builder signer(Signer value) {
            this.signer = Objects.requireNonNull(value, "signer");
            if (this.checkoutSigner == null) {
                this.checkoutSigner = value;
            }
            return this;
        }

        /**
         * Replaces the digest algorithm for checkout operations only.
         *
         * @param value the signer
         * @return this builder
         */
        public Builder checkoutSigner(Signer value) {
            this.checkoutSigner = Objects.requireNonNull(value, "checkoutSigner");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         */
        public SelcomClient build() {
            return new SelcomClient(this);
        }
    }
}
