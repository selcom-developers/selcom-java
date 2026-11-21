# selcom4j
[![CI](https://github.com/selcom-developers/selcom4j/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/selcom-developers/selcom4j/actions/workflows/ci.yml)

Community Java client for the Selcom API gateway: request signing, a replaceable transport, typed checkout and payout operations, result classification, webhook verification and log redaction.

Documentation: <https://selcom-developers.github.io/>

> **Not affiliated with Selcom Paytech Ltd.** For API access, credentials, contracts and support, go to Selcom: <https://developers.selcommobile.com/>.

- Java 17 or later.
- One runtime dependency: Jackson databind, for JSON.

## Contents

- [Install](#install)
- [Quickstart](#quickstart)
- [Configuration](#configuration)
- [Operations](#operations)
- [Results and error handling](#results-and-error-handling)
- [Idempotency and reconciliation](#idempotency-and-reconciliation)
- [Webhooks](#webhooks)
- [Using the layers on their own](#using-the-layers-on-their-own)
- [Logging and redaction](#logging-and-redaction)
- [Security](#security)
- [Development](#development)

## Install

Available through [JitPack](https://jitpack.io/#selcom-developers/selcom4j). Add the repository, then the dependency.

Maven:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.selcom-developers</groupId>
  <artifactId>selcom4j</artifactId>
  <version>v0.1.2</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.selcom-developers:selcom4j:v0.1.2")
}
```

To build from source instead: `git clone https://github.com/selcom-developers/selcom4j.git && cd selcom4j && mvn install`.

## Quickstart

```java
import io.github.selcomdevelopers.selcom.*;
import io.github.selcomdevelopers.selcom.api.*;
import io.github.selcomdevelopers.selcom.result.*;

SelcomConfig config = SelcomConfig.builder()
        .environment(Environment.SANDBOX)            // required, no default
        .credentials(System.getenv("SELCOM_API_KEY"), System.getenv("SELCOM_API_SECRET"))
        .vendor(System.getenv("SELCOM_VENDOR"))
        .pin(System.getenv("SELCOM_PIN"))            // only needed for payouts
        .build();

SelcomClient client = SelcomClient.create(config);

// 1. Create an order and send the buyer to the hosted page.
Result<Responses.OrderCreated> order = client.createOrder(CreateOrderRequest.builder()
        .orderId("ORD-1001")
        .buyerEmail("test.buyer@example.com")
        .buyerName("Test Buyer")
        .buyerPhone("255700000001")
        .amount(Money.tzs(8000))
        .paymentMethods(PaymentMethod.ALL)
        .webhookUrl("https://shop.example.com/selcom/webhook/9f1c2e")   // plain URL; base64-encoded for you
        .billing(new Address("Test", "Buyer", "1 Test Street", null, "Dar es Salaam", "Dar es Salaam", "11101", "TZ", "255700000001"))
        .noOfItems(1)
        .build());

if (order.status() == Status.SUCCESS) {
    String payUrl = order.first().orElseThrow().paymentGatewayUrl();   // decoded for you; the raw base64 is kept too
}

// 2. Later, confirm. Order status is the source of truth, never a webhook.
Result<Responses.OrderStatusData> status = client.orderStatus(new Requests.OrderStatus("ORD-1001"));
```

## Configuration

| Setting | Notes |
|---|---|
| `environment(SANDBOX \| PRODUCTION)` | **Required. No default**, and in particular no production default: building without one throws `ConfigurationException`. Sandbox is `https://apigwtest.selcommobile.com`, production `https://apigw.selcommobile.com`. |
| `customBaseUrl(url)` | For a simulator or a proxy. Its own explicit choice: it cannot be combined with `environment`, and must be `https` (or `http` on localhost). |
| `credentials(apiKey, secret)` | Required. A blank key or secret throws at construction. |
| `checkoutCredentials(apiKey, secret)` | Optional separate key pair for `checkout.*` operations. |
| `vendor(id)` | Required. Filled into every request that needs it. |
| `pin(pin)` | The float PIN. Needed only for payouts; a payout without one fails before anything is sent. |
| `queryTimeouts(Timeouts)` | Default connect 10 s, read 10 s. Used by queries and everything that moves no money. |
| `paymentTimeouts(Timeouts)` | Default connect 15 s, read 180 s. The long read timeout is deliberate: timing out early does not stop a payment, it turns a known answer into an unknown one. |
| `zone(ZoneId)` | Timestamp offset. Default `Africa/Dar_es_Salaam` (`+03:00`). |

The secret and PIN never appear in `toString()` of any class here, or in exception messages.

`SelcomClient.builder(config)` also lets you replace the `Transport`, the `Clock` and the `Signer`.

## Operations

One typed method per operation. Requests are records (or builders for order creation) that validate themselves; the vendor and PIN come from the configuration. Each method returns a `Result<T>`.

| Method | Request | Operation | Kind |
|---|---|---|---|
| `createOrder` | `CreateOrderRequest` | `checkout.create-order` | write |
| `createOrderMinimal` | `CreateOrderMinimalRequest` | `checkout.create-order-minimal` | write |
| `walletPayment` | `Requests.WalletPayment` | `checkout.wallet-payment` | payment |
| `cardPayment` | `Requests.CardPayment` | `checkout.card-payment` | payment |
| `orderStatus` | `Requests.OrderStatus` | `checkout.order-status` | order status |
| `cancelOrder` | `Requests.CancelOrder` | `checkout.cancel-order` | write |
| `listOrders` | `Requests.ListOrders` | `checkout.list-orders` | read |
| `storedCards` | `Requests.StoredCards` | `checkout.stored-cards` | read |
| `deleteCard` | `Requests.DeleteCard` | `checkout.delete-card` | write |
| `utilityPayment` | `Requests.UtilityPayment` | `utilitypayment.process` | payment |
| `utilityLookup` | `Requests.UtilityLookup` | `utilitypayment.lookup` | read |
| `utilityQuery` | `Requests.UtilityQuery` | `utilitypayment.query` | status query |
| `walletCashIn` | `Requests.WalletCashIn` | `walletcashin.process` | payment |
| `walletNameLookup` | `Requests.WalletNameLookup` | `walletcashin.namelookup` | read |
| `walletCashInQuery` | `Requests.WalletCashInQuery` | `walletcashin.query` | status query |
| `vendorBalance` | `Requests.VendorBalance` | `vendor.balance` | read |

Notes:

- **Money** is `Money(units, currency)`: whole units, never a floating-point number. The currency is always explicit; payouts accept TZS only and reject anything else. Amounts in responses are strings (sometimes `"10,000"`); `Money.parse` reads them strictly and rejects fractions.
- **URLs** (`redirectUrl`, `cancelUrl`, `webhookUrl`) are given as plain URLs and base64-encoded for you before signing, as the API requires. `payment_gateway_url` in responses is decoded for you; the raw value is kept.
- **Billing and shipping** are sent as dotted keys (`billing.firstname`), never as nested objects.
- **IDs**: `transid` and `order_id` must be unique per vendor and are the idempotency keys. Keep them to 30 characters or fewer using `A-Z a-z 0-9 -`.
- Anything this library does not model yet: `client.execute(Operation, Map)` signs, sends and classifies parameters you built yourself, and `client.execute(ApiRequest, reader)` does the same for your own request type.

## Results and error handling

Every method returns a `Result` with:

| Field | Meaning |
|---|---|
| `status()` | `SUCCESS`, `PENDING`, `AMBIGUOUS`, `FAILED` or `NOT_FOUND` |
| `resultCode()`, `result()`, `message()` | The gateway's own code and text, unchanged |
| `reference()`, `transId()`, `orderId()` | Identifiers |
| `httpStatus()` | The raw HTTP status |
| `data()`, `first()` | The decoded `data` entries, typed per operation |
| `raw()` | The raw body, for audit. Redact before logging |
| `isFinal()` | True for `SUCCESS`, `FAILED` and `NOT_FOUND` |

Classification looks at `resultcode` only (the HTTP status and the `result` text do not decide it):

| `resultcode` | Status |
|---|---|
| `000` | `SUCCESS` (for `orderStatus`, the verdict is `data[0].payment_status`) |
| `111`, `927` | `PENDING` |
| `999` | `AMBIGUOUS` |
| `404` on a status query or order status | `NOT_FOUND` |
| anything else | `FAILED` (for `orderStatus`: `AMBIGUOUS`, because the query failed, not the order) |

A response with no verdict (an HTML error page, an empty body) is `AMBIGUOUS` for payments and queries, because the money may have moved, and an `InvalidResponseException` (carrying the HTTP status and raw body) for operations that move no money.

**Exceptions** (all extend `SelcomException`, all unchecked):

| Exception | Meaning |
|---|---|
| `SigningException` | The request cannot be signed (empty key or secret, fractional amount, bad key, bad timestamp). **Nothing was sent.** Never catch it and carry on. |
| `TransportException` | No HTTP response was received. `outcome()` is `NOT_SENT` (DNS failure, connection refused or timed out, TLS handshake failed: the request never reached the gateway, so sending again with the same ID is safe) or `UNKNOWN_OUTCOME` (anything after the request may have been written: treat as `AMBIGUOUS`, query, never resend). If the HTTP library cannot tell, it is `UNKNOWN_OUTCOME`. A transport error is never turned into `FAILED`. |
| `InvalidResponseException` | See above. |
| `ConfigurationException` | Bad configuration, or a payout attempted without a PIN. |

**No automatic retry.** The client never retries anything. For a payment or a write that is the rule; for reads it is a choice, and if you retry a read after `NOT_SENT`, do it yourself with a fresh call (a fresh timestamp is generated each time).

## Idempotency and reconciliation

The ID is the idempotency key. The gateway refuses a second transaction with an ID it has seen. This client is stateless between calls: it does not lock, queue or deduplicate. Staying safe is your job:

1. **Persist the ID first.** Generate `transid` and `order_id` deterministically from your own records (for example the payout row's primary key with a prefix), save them with a "submitting" state, then call. After a crash, reconcile anything left in that state by querying.
2. **Put a unique constraint on the ID**, so a retried job cannot create a second transaction.
3. **Never retry a money-moving call**, not even with the same ID. After a timeout, learn the answer by asking.
4. **Settle `PENDING` and `AMBIGUOUS` by querying:** wait at least 3 minutes, then call `utilityQuery` or `walletCashInQuery` by `transid`, or `orderStatus` by `order_id`. Repeat every 3 minutes for a bounded window you choose. If it never settles, hand it to a person, who raises it with Selcom support with the `transid` and `reference`.
5. **Never mint a new ID while an outcome is unknown.** Use a new ID only after the old one is `FAILED`.
6. **Resubmit the same ID only after `NOT_FOUND`** from a status query. If the original did arrive in the meantime, the gateway refuses the duplicate.
7. **Close an unpaid order with `cancelOrder`.** A completed order cannot be cancelled, so a successful cancel proves it was never paid. A `FAILED` from `orderStatus` describes the latest attempt only; the order can still be paid until it expires or is cancelled. If a `walletPayment` fails outright, cancel the order on a best-effort basis.
8. **Apply results once.** Apply them through one function keyed on `(order_id, transid)`, under a row lock or a compare-and-set, so that a webhook and a poll arriving together cannot both win, and a later `FAILED` never overwrites a `SUCCESS`.

A status query can also fail because the query itself was rejected (for example, a bad signature). Do not pay again on the strength of a single `FAILED` from a status query without reading its `resultCode()` and `message()`.

## Webhooks

**A webhook is a hint, not proof.** Never fulfil an order because a webhook said so. Confirm with `orderStatus` first, every time, and take the amount from there.

```java
WebhookVerifier verifier = WebhookVerifier.hs256(secret);   // 300 s window, in-memory replay store

// In your HTTP handler: read the RAW body before any framework parses it.
Verdict verdict = verifier.verify(headers, rawBody);
response.status(verdict.httpStatus());          // 200 for VALID, UNSIGNED, REPLAY; 401 for INVALID, STALE
if (verdict.shouldProcess()) {
    WebhookPayload payload = WebhookPayload.parse(rawBody);
    // 1. de-duplicate on (payload.orderId(), payload.transId()) in your own store
    // 2. store the delivery (redacted), acknowledge with 200
    // 3. then, in a background job, call client.orderStatus(...) and act only on what it returns
}
```

| Verdict | Respond | Process? |
|---|---|---|
| `VALID` | 200 | Yes, then confirm with order status |
| `UNSIGNED` (no `Digest` header) | 200 | Yes, then confirm. Order status is the only protection |
| `REPLAY` | 200 | No, it has been handled |
| `INVALID` | 401 | No. Log it, redacted |
| `STALE` | 401 | No. Log it, redacted |

- If your `ReplayStore` fails, `verify` throws `ReplayStoreException`: answer 5xx so the gateway delivers again.
- The signed field list must include `order_id` and `transid`, and a signed name or value may not contain `&`; otherwise the verdict is `INVALID`.
- `VALID` vouches only for the fields named in `Signed-Fields`. The amount is usually not one of them.
- Use `new WebhookVerifier(signer, clock, window, replayStore)` for another window or clock, and plug a shared `ReplayStore` in when more than one process receives webhooks. The in-memory store is per process.
- Which key the gateway signs webhooks with is not confirmed in Selcom's documentation; the samples assume your API secret. Until you have confirmed it in the sandbox, consider logging verdicts without rejecting.
- Return 200 as soon as the delivery is stored. Never return an error because the order-status call was slow or fulfilment failed. Webhooks can be lost: poll `orderStatus` (first after 3 minutes, then every 3 minutes, for a bounded window).
- Send the webhook URL on every order, over HTTPS, with an unguessable token in the path.

## Using the layers on their own

- **Signer:** `RequestSigner.sign(method, params, timestamp, apiKey, signer)` is a pure function with no I/O and no clock. It returns the flattened fields, `Signed-Fields`, the signing string, the five signing headers, and the query string (GET, DELETE) or the flat JSON body (POST). Pass parameters in a `LinkedHashMap`: the order is part of the signature. Implement `Signer` (`method()` and `sign(String)`) to add another algorithm.
- **JDK retry behaviour:** by default the JDK resends a GET once if a pooled connection was dropped; that is harmless for reads. The JDK property `jdk.httpclient.enableAllMethodRetry=true` makes it resend any method, which conflicts with the rule that a payment or write is sent exactly once. `JdkHttpTransport` therefore throws a `ConfigurationException` before sending any POST or DELETE while that property is set. Unset it, or use your own transport.
- **Transport:** implement `Transport` (`TransportResponse send(TransportRequest)`) to use your own HTTP client, a proxy, logging or a test fake, and pass it to `SelcomClient.builder(config).transport(...)`. It must apply the request's timeouts, follow no redirects, retry nothing (including inside its HTTP library, for POST and DELETE), and throw a `TransportException` classified `NOT_SENT` or `UNKNOWN_OUTCOME` when no response arrives. `JdkHttpTransport` is the default; give it your own `HttpClient` for a proxy or custom TLS.
- **Classifier:** `Classifier.classify(kind, body, httpStatus, rawBody)` maps a decoded response to a `Status`.
- **Operations:** every request implements `ApiRequest`; `params(defaults)` shows exactly what will be signed and sent.

## Logging and redaction

Never log the secret, the PIN, the `Authorization` header or the `Digest` header, at any level. `Redactor` applies the rules to headers, flat or nested JSON bodies, maps and query strings:

| Data | Fields | Redacted form |
|---|---|---|
| Secrets | `pin`, `secret`, `api_secret`, `Authorization`, `Digest`, `card_token` | `[REDACTED]` |
| Phone numbers | `msisdn`, `phone`, `buyer_phone` (and `utilityref` for wallet cash-in, with `Redactor.treatingUtilityRefAsPhone()`) | `********0001` |
| E-mail | `buyer_email`, `email` | `t***@example.com` |
| Names | `buyer_name`, `firstname`, `lastname`, `name` | `T***` |

`billing.` and `shipping.` forms are covered. `toString()` of requests, results, `TransportRequest` and the response data records already redact. Use `Redactor` for anything you store or log, including `Result.raw()`.

## Security

- Keep the secret and PIN out of source control and logs; read them from the environment or a secret store.
- A signing error means nothing was sent. Do not catch it and retry without fixing the cause. The client never sends an empty `Digest`.
- Compare digests in constant time (the webhook verifier does).
- Do not invent buyer data. If a required field is missing, the client throws; it does not fill in placeholders.
- Report vulnerabilities privately: see [SECURITY.md](SECURITY.md).

## Development

```
mvn verify                       # compile with -Xlint:all -Werror, test, build jar + sources + javadoc
scripts/refresh-vectors.sh       # refresh the pinned copy of the spec vectors
```

Tests run every case and reject in `vectors/signing.json`, every scenario in `vectors/responses/`, and every webhook sample, from `src/test/resources/vectors/` (spec version in [`SPEC_VERSION`](SPEC_VERSION)). They make no external network calls. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE).
