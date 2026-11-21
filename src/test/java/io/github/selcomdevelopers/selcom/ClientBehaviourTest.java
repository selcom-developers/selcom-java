package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.api.Requests;
import io.github.selcomdevelopers.selcom.api.Responses;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.result.Result;
import io.github.selcomdevelopers.selcom.result.Status;
import io.github.selcomdevelopers.selcom.signing.Signer;
import io.github.selcomdevelopers.selcom.signing.SigningException;
import io.github.selcomdevelopers.selcom.transport.TransportException;
import io.github.selcomdevelopers.selcom.transport.TransportRequest;
import io.github.selcomdevelopers.selcom.transport.TransportResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientBehaviourTest {
    private static final String OK = "{\"reference\":\"REF0000000001\",\"resultcode\":\"000\",\"result\":\"SUCCESS\",\"message\":\"ok\",\"data\":[]}";

    @Test
    void getRequestCarriesSignedQueryAndHeaders() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Samples.client(transport).orderStatus(new Requests.OrderStatus("ORD-1001"));
        TransportRequest sent = transport.last();
        assertEquals("https://apigwtest.selcommobile.com/v1/checkout/order-status?order_id=ORD-1001", sent.uri().toString());
        assertNull(sent.body());
        assertEquals("2026-11-21T10:30:46+03:00", sent.headers().get("Timestamp"));
        assertEquals("order_id", sent.headers().get("Signed-Fields"));
        assertEquals("HS256", sent.headers().get("Digest-Method"));
        assertEquals("application/json", sent.headers().get("Accept"));
        assertTrue(sent.headers().get("User-Agent").startsWith("selcom4j/0.1.1"), sent.headers().get("User-Agent"));
        assertEquals(Timeouts.QUERY, sent.timeouts());
    }

    @Test
    void postBodyIsFlatSignedAndUsesPaymentTimeouts() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Samples.client(transport).utilityPayment(new Requests.UtilityPayment("TXN-UTL-0001", "LUKU", "01234567890", Money.tzs(5000), null));
        TransportRequest sent = transport.last();
        JsonNode body = Json.parse(sent.body());
        assertEquals("application/json", sent.headers().get("Content-Type"));
        assertEquals("transid,utilitycode,utilityref,amount,vendor,pin", sent.headers().get("Signed-Fields"));
        assertTrue(body.get("amount").isIntegralNumber());
        assertEquals("TILL123", body.get("vendor").asText());
        assertEquals("4321", body.get("pin").asText());
        assertEquals(Timeouts.PAYMENT, sent.timeouts());
    }

    @Test
    void createOrderSendsFlatBillingBase64UrlsAndExplicitCurrency() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Samples.client(transport).createOrder(Samples.createOrder());
        JsonNode body = Json.parse(transport.last().body());
        assertEquals("TZS", body.get("currency").asText());
        assertEquals(8000, body.get("amount").asLong());
        assertEquals("ALL", body.get("payment_methods").asText());
        assertEquals("aHR0cHM6Ly9zaG9wLmV4YW1wbGUuY29tL3JldHVybg==", body.get("redirect_url").asText());
        assertEquals("Test", body.get("billing.firstname").asText());
        assertFalse(body.has("billing"));
        assertTrue(transport.last().headers().get("Signed-Fields").contains("billing.firstname,billing.lastname,billing.address_1"));
        assertFalse(body.has("billing.address_2"), "absent optional fields are not sent");
    }

    @Test
    void aFractionalAmountIsRefusedBeforeAnythingIsSent() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        SelcomClient client = Samples.client(transport);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("order_id", "ORD-1001");
        params.put("amount", 1.5);
        SigningException e = assertThrows(SigningException.class, () -> client.execute(Operation.CHECKOUT_CREATE_ORDER, params));
        assertEquals(SigningException.Code.NON_INTEGER_NUMBER, e.code());
        assertTrue(transport.requests.isEmpty(), "nothing may be sent after a signing error");
    }

    @Test
    void aSignerThatReturnsAnEmptyDigestSendsNothing() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Signer broken = new Signer() {
            @Override public String method() {
                return "HS256";
            }

            @Override public String sign(String signingString) {
                return "";
            }
        };
        SelcomClient client = SelcomClient.builder(Samples.config()).transport(transport).clock(Samples.CLOCK).signer(broken).build();
        SigningException e = assertThrows(SigningException.class, () -> client.orderStatus(new Requests.OrderStatus("ORD-1001")));
        assertEquals(SigningException.Code.EMPTY_DIGEST, e.code());
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void aPaymentIsNeverRetriedWhateverTheError() {
        for (TransportException.Outcome outcome : TransportException.Outcome.values()) {
            AtomicInteger calls = new AtomicInteger();
            FakeTransport transport = new FakeTransport(request -> {
                calls.incrementAndGet();
                throw new TransportException(outcome, "boom", null);
            });
            SelcomClient client = Samples.client(transport);
            TransportException e = assertThrows(TransportException.class,
                    () -> client.walletCashIn(new Requests.WalletCashIn("TXN-CSH-0001", "CASHIN", "255700000002", Money.tzs(1000), null)));
            assertEquals(outcome, e.outcome());
            assertEquals(1, calls.get(), "payments are sent exactly once");
        }
    }

    @Test
    void writesAndQueriesAreSentOnceToo() {
        AtomicInteger calls = new AtomicInteger();
        FakeTransport transport = new FakeTransport(request -> {
            calls.incrementAndGet();
            throw new TransportException(TransportException.Outcome.NOT_SENT, "refused", null);
        });
        SelcomClient client = Samples.client(transport);
        assertThrows(TransportException.class, () -> client.createOrder(Samples.createOrder()));
        assertThrows(TransportException.class, () -> client.orderStatus(new Requests.OrderStatus("ORD-1")));
        assertEquals(2, calls.get());
    }

    @Test
    void anUnclassifiedTransportFailureBecomesUnknownOutcomeNeverFailed() {
        FakeTransport transport = new FakeTransport(request -> {
            throw new IllegalStateException("custom transport bug");
        });
        TransportException e = assertThrows(TransportException.class,
                () -> Samples.client(transport).utilityQuery(new Requests.UtilityQuery("TXN-UTL-0001")));
        assertEquals(TransportException.Outcome.UNKNOWN_OUTCOME, e.outcome());
    }

    @Test
    void resultCarriesTheDecodedDataAndTheIds() {
        String body = "{\"reference\":\"REF0000000001\",\"resultcode\":\"000\",\"result\":\"SUCCESS\",\"message\":\"ok\",\"data\":"
                + "[{\"gateway_buyer_uuid\":\"GBU-TEST-0001\",\"payment_token\":\"80000001\",\"qr\":\"QR\","
                + "\"payment_gateway_url\":\"aHR0cHM6Ly9jaGVja291dC5leGFtcGxlLmNvbS9wYXkvT1JELTEwMDU=\"}]}";
        Result<Responses.OrderCreated> result = Samples.client(FakeTransport.replying(200, body)).createOrder(Samples.createOrder());
        assertEquals(Status.SUCCESS, result.status());
        assertEquals("000", result.resultCode());
        assertEquals("ORD-1001", result.orderId(), "taken from the request when the response has none");
        Responses.OrderCreated created = result.first().orElseThrow();
        assertEquals("GBU-TEST-0001", created.gatewayBuyerUuid());
        assertEquals("https://checkout.example.com/pay/ORD-1005", created.paymentGatewayUrl());
        assertEquals("aHR0cHM6Ly9jaGVja291dC5leGFtcGxlLmNvbS9wYXkvT1JELTEwMDU=", created.paymentGatewayUrlRaw());
        assertFalse(result.toString().contains("80000001"));
    }

    @Test
    void orderStatusReadsMsisdnOrPhoneAndMapsThePaymentStatus() {
        String body = "{\"resultcode\":\"000\",\"data\":[{\"order_id\":\"ORD-1003\",\"amount\":\"5,000\",\"payment_status\":\"completed\",\"msisdn\":\"255700000001\"}]}";
        Result<Responses.OrderStatusData> result = Samples.client(FakeTransport.replying(200, body)).orderStatus(new Requests.OrderStatus("ORD-1003"));
        Responses.OrderStatusData data = result.first().orElseThrow();
        assertEquals("255700000001", data.phone());
        assertEquals(Status.SUCCESS, data.status());
        assertEquals(5000, data.amount(Currency.TZS).units());
        assertFalse(data.toString().contains("255700000001"));
    }

    @Test
    void listOrdersSendsIsoDates() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Samples.client(transport).listOrders(new Requests.ListOrders(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 21)));
        assertEquals("fromdate=2026-11-01&todate=2026-11-21", transport.last().uri().getRawQuery());
    }

    @Test
    void separateCheckoutCredentialsAreUsedForCheckoutOnly() {
        SelcomConfig config = SelcomConfig.builder().environment(Environment.SANDBOX)
                .credentials("test-key-0001", "test-secret-0001")
                .checkoutCredentials("test-key-0002", "test-secret-0002")
                .vendor("TILL123").pin("4321").build();
        FakeTransport transport = FakeTransport.replying(200, OK);
        SelcomClient client = SelcomClient.builder(config).transport(transport).clock(Samples.CLOCK).build();
        client.orderStatus(new Requests.OrderStatus("ORD-1"));
        String checkoutAuth = transport.last().headers().get("Authorization");
        client.vendorBalance(new Requests.VendorBalance("TXN-BAL-0001"));
        String payoutAuth = transport.last().headers().get("Authorization");
        assertEquals("SELCOM " + java.util.Base64.getEncoder().encodeToString("test-key-0002".getBytes(java.nio.charset.StandardCharsets.UTF_8)), checkoutAuth);
        assertEquals("SELCOM " + java.util.Base64.getEncoder().encodeToString("test-key-0001".getBytes(java.nio.charset.StandardCharsets.UTF_8)), payoutAuth);
    }

    @Test
    void aPayoutWithoutAPinFailsBeforeSending() {
        SelcomConfig config = SelcomConfig.builder().environment(Environment.SANDBOX)
                .credentials("test-key-0001", "test-secret-0001").vendor("TILL123").build();
        FakeTransport transport = FakeTransport.replying(200, OK);
        SelcomClient client = SelcomClient.builder(config).transport(transport).clock(Samples.CLOCK).build();
        assertThrows(ConfigurationException.class, () -> client.vendorBalance(new Requests.VendorBalance("TXN-BAL-0001")));
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void theTransportRequestAndTheRequestObjectsRedactWhenPrinted() {
        FakeTransport transport = FakeTransport.replying(200, OK);
        Samples.client(transport).walletCashIn(new Requests.WalletCashIn("TXN-CSH-0001", "CASHIN", "255700000002", Money.tzs(1000), "255700000003"));
        String printed = transport.last().toString();
        assertFalse(printed.contains("4321"), printed);
        assertFalse(printed.contains(transport.last().headers().get("Digest")), printed);
        assertFalse(printed.contains(transport.last().headers().get("Authorization")), printed);
        assertFalse(printed.contains("255700000003"), printed);
        assertFalse(printed.contains("255700000002"), "utilityref is a phone number on wallet cash-in: " + printed);
        assertTrue(printed.contains("*******0002") || printed.contains("********0002"), printed);
        String request = new Requests.WalletCashIn("TXN-CSH-0001", "CASHIN", "255700000002", Money.tzs(1000), null).toString();
        assertFalse(request.contains("255700000002"), request);
        assertFalse(request.contains("pin=-"), request);
    }

    @Test
    void theDefaultTransportRefusalSurfacesAsConfigurationExceptionNotTransportException() {
        String property = "jdk.httpclient.enableAllMethodRetry";
        String old = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            SelcomConfig config = SelcomConfig.builder().customBaseUrl("http://localhost:1")
                    .credentials("test-key-0001", "test-secret-0001").vendor("TILL123").pin("4321").build();
            SelcomClient client = SelcomClient.builder(config).clock(Samples.CLOCK).build();
            assertThrows(ConfigurationException.class,
                    () -> client.utilityPayment(new Requests.UtilityPayment("TXN-UTL-0001", "LUKU", "01234567890", Money.tzs(5000), null)));
        } finally {
            if (old == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, old);
            }
        }
    }

    @Test
    void walletCashInRequestsAreMaskedEvenUnderACustomBasePathPrefix() {
        SelcomConfig config = SelcomConfig.builder().customBaseUrl("https://proxy.example.com/selcom")
                .credentials("test-key-0001", "test-secret-0001").vendor("TILL123").pin("4321").build();
        FakeTransport transport = FakeTransport.replying(200, OK);
        SelcomClient.builder(config).transport(transport).clock(Samples.CLOCK).build()
                .walletNameLookup(new Requests.WalletNameLookup("CASHIN", "255700000002", "TXN-NML-0001"));
        assertEquals("/selcom/v1/walletcashin/namelookup", transport.last().uri().getPath());
        String printed = transport.last().toString();
        assertFalse(printed.contains("255700000002"), printed);
    }

    @Test
    void aNonJsonBodyOnAPaymentIsAmbiguousNotAnError() {
        Result<Map<String, Object>> result = Samples.client(FakeTransport.replying(502, "<html>Bad gateway</html>"))
                .utilityPayment(new Requests.UtilityPayment("TXN-UTL-0001", "LUKU", "01234567890", Money.tzs(5000), null));
        assertEquals(Status.AMBIGUOUS, result.status());
        assertNull(result.resultCode());
        assertEquals(502, result.httpStatus());
        assertEquals("<html>Bad gateway</html>", result.raw());
        assertEquals("TXN-UTL-0001", result.transId());
    }

    @Test
    void theHttpStatusDoesNotDecideTheVerdict() {
        Result<Map<String, Object>> result = Samples.client(FakeTransport.replying(500, "{\"resultcode\":\"000\",\"result\":\"FAIL\",\"data\":[]}"))
                .cancelOrder(new Requests.CancelOrder("ORD-1"));
        assertEquals(Status.SUCCESS, result.status());
        assertEquals(500, result.httpStatus());
    }

    @Test
    void transportResponseReplacesNullBody() {
        assertEquals("", new TransportResponse(204, null).body());
    }
}
