package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.selcomdevelopers.selcom.signing.Hs256Signer;
import io.github.selcomdevelopers.selcom.webhook.InMemoryReplayStore;
import io.github.selcomdevelopers.selcom.webhook.Verdict;
import io.github.selcomdevelopers.selcom.webhook.WebhookPayload;
import io.github.selcomdevelopers.selcom.webhook.WebhookVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookBehaviourTest {
    private static final String SECRET = "81dc9bdb52d04dc20036dbd8313ed055";
    private static final String BODY = "{\"result\":\"SUCCESS\",\"resultcode\":\"000\",\"order_id\":\"ORD-2001\",\"transid\":\"TXN-WLT-2001\","
            + "\"reference\":\"REF0000002001\",\"channel\":\"AIRTELMONEY\",\"amount\":\"10000\",\"phone\":\"255700000001\",\"payment_status\":\"COMPLETED\"}";
    private static final String FIELDS = "transid,order_id,reference,result,resultcode,payment_status";

    private static Map<String, String> signed(String timestamp) {
        String signingString = "timestamp=" + timestamp
                + "&transid=TXN-WLT-2001&order_id=ORD-2001&reference=REF0000002001&result=SUCCESS&resultcode=000&payment_status=COMPLETED";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("digest", new Hs256Signer(SECRET).sign(signingString));
        headers.put("DIGEST-METHOD", "HS256");
        headers.put("timestamp", timestamp);
        headers.put("signed-fields", FIELDS);
        return headers;
    }

    private static WebhookVerifier verifier(String now, Duration window) {
        return WebhookVerifier.hs256(SECRET, Clock.fixed(Instant.parse(now), ZoneOffset.UTC), window, new InMemoryReplayStore());
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW)
                .verify(signed("2026-11-21T10:30:46+03:00"), BODY));
    }

    @Test
    void theWindowIsConfigurableInBothDirections() {
        Map<String, String> headers = signed("2026-11-21T10:30:46+03:00"); // 07:30:46Z
        assertEquals(Verdict.STALE, verifier("2026-11-21T07:36:00Z", WebhookVerifier.DEFAULT_WINDOW).verify(headers, BODY));
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:36:00Z", Duration.ofMinutes(10)).verify(headers, BODY));
        assertEquals(Verdict.STALE, verifier("2026-11-21T07:20:00Z", WebhookVerifier.DEFAULT_WINDOW).verify(headers, BODY), "timestamp in the future");
    }

    @Test
    void theBasicOffsetFormIsAcceptedAndSignedAsGiven() {
        String timestamp = "2026-11-21T10:30:46+0300";
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW).verify(signed(timestamp), BODY));
    }

    @Test
    void malformedInputsAreInvalidNeverAnException() {
        WebhookVerifier verifier = verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW);
        Map<String, String> ok = signed("2026-11-21T10:30:46+03:00");
        assertEquals(Verdict.INVALID, verifier.verify(ok, "not json"));
        assertEquals(Verdict.INVALID, verifier.verify(ok, "[1,2]"));
        assertEquals(Verdict.INVALID, verifier.verify(ok, null));
        Map<String, String> badStamp = signed("2026-11-21T10:30:46+03:00");
        badStamp.put("timestamp", "yesterday");
        assertEquals(Verdict.INVALID, verifier.verify(badStamp, BODY));
        Map<String, String> noFields = signed("2026-11-21T10:30:46+03:00");
        noFields.put("signed-fields", "");
        assertEquals(Verdict.INVALID, verifier.verify(noFields, BODY));
        Map<String, String> wrongMethod = signed("2026-11-21T10:30:46+03:00");
        wrongMethod.put("DIGEST-METHOD", "RS256");
        assertEquals(Verdict.INVALID, verifier.verify(wrongMethod, BODY));
        Map<String, String> nestedField = signed("2026-11-21T10:30:46+03:00");
        nestedField.put("signed-fields", "transid,order_id,data");
        assertEquals(Verdict.INVALID, verifier.verify(nestedField, "{\"transid\":\"a\",\"order_id\":\"b\",\"data\":{\"x\":1}}"));
    }

    @Test
    void dottedSignedFieldsAreFoundFlatOrNested() {
        String timestamp = "2026-11-21T10:30:46+03:00";
        String digest = new Hs256Signer(SECRET).sign("timestamp=" + timestamp + "&order_id=ORD-1&transid=TXN-1&billing.firstname=John");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Digest", digest);
        headers.put("Digest-Method", "HS256");
        headers.put("Timestamp", timestamp);
        headers.put("Signed-Fields", "order_id,transid,billing.firstname");
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW)
                .verify(headers, "{\"order_id\":\"ORD-1\",\"transid\":\"TXN-1\",\"billing\":{\"firstname\":\"John\"}}"));
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW)
                .verify(headers, "{\"order_id\":\"ORD-1\",\"transid\":\"TXN-1\",\"billing.firstname\":\"John\"}"));
    }

    @Test
    void extremeNumbersInSignedFieldsAreInvalidQuicklyAndVerifyNeverThrows() {
        WebhookVerifier verifier = verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Digest", "garbage=");
        headers.put("Digest-Method", "HS256");
        headers.put("Timestamp", "2026-11-21T10:30:46+03:00");
        headers.put("Signed-Fields", "order_id");
        for (String body : new String[] {"{\"order_id\":1e10000000}", "{\"order_id\":1e100000000}", "{\"order_id\":1e-999999999}",
                "{\"order_id\":-1e-999999999}", "{\"order_id\":1e999999999999}"}) {
            Verdict verdict = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> verifier.verify(headers, body), body);
            assertEquals(Verdict.INVALID, verdict, body);
        }
    }

    @Test
    void aFailingReplayStoreGivesInvalidNotAnException() {
        WebhookVerifier verifier = WebhookVerifier.hs256(SECRET, Clock.fixed(Instant.parse("2026-11-21T07:31:00Z"), ZoneOffset.UTC),
                WebhookVerifier.DEFAULT_WINDOW, (digest, now, expiresAt) -> {
                    throw new IllegalStateException("store down");
                });
        assertEquals(Verdict.INVALID, verifier.verify(signed("2026-11-21T10:30:46+03:00"), BODY));
    }

    @Test
    void replayEntriesLiveUntilTheSignedTimePlusTheWindow() {
        // Signed 9 minutes in the future, window 10 minutes: valid now, and still valid 15 minutes later.
        String signedAt = "2026-11-21T10:39:46+03:00"; // 07:39:46Z
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T07:30:46Z"));
        WebhookVerifier verifier = WebhookVerifier.hs256(SECRET, clock, Duration.ofMinutes(10), new InMemoryReplayStore());
        Map<String, String> headers = signed(signedAt);
        assertEquals(Verdict.VALID, verifier.verify(headers, BODY));
        clock.set(Instant.parse("2026-11-21T07:45:46Z")); // 6 minutes after the signed time, 15 after first arrival
        assertEquals(Verdict.REPLAY, verifier.verify(headers, BODY), "entry lives until signed time plus window");
        clock.set(Instant.parse("2026-11-21T07:50:47Z")); // beyond signed + window
        assertEquals(Verdict.STALE, verifier.verify(headers, BODY));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant value) {
            this.now = value;
        }

        @Override public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }

    private static Verdict verifyWith(String fields, String body) {
        String timestamp = "2026-11-21T10:30:46+03:00";
        String digest = new Hs256Signer(SECRET).sign("timestamp=" + timestamp + signingTail(fields, body));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Digest", digest);
        headers.put("Digest-Method", "HS256");
        headers.put("Timestamp", timestamp);
        headers.put("Signed-Fields", fields);
        return verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW).verify(headers, body);
    }

    private static String signingTail(String fields, String body) {
        com.fasterxml.jackson.databind.JsonNode node = io.github.selcomdevelopers.selcom.internal.Json.parse(body);
        StringBuilder out = new StringBuilder();
        for (String field : fields.split(",")) {
            out.append('&').append(field).append('=').append(node.get(field).asText());
        }
        return out.toString();
    }

    @Test
    void signedFieldsMustIncludeOrderIdAndTransid() {
        String body = "{\"order_id\":\"ORD-2001\",\"transid\":\"TXN-1\",\"reference\":\"R1\"}";
        assertEquals(Verdict.VALID, verifyWith("order_id,transid,reference", body));
        assertEquals(Verdict.INVALID, verifyWith("order_id,reference", body), "no transid");
        assertEquals(Verdict.INVALID, verifyWith("transid,reference", body), "no order_id");
        assertEquals(Verdict.INVALID, verifyWith("reference", body));
    }

    @Test
    void anAmpersandInASignedValueOrNameIsInvalidEvenWithAMatchingDigest() {
        assertEquals(Verdict.INVALID, verifyWith("order_id,transid",
                "{\"order_id\":\"ORD-1&transid=X\",\"transid\":\"TXN-1\"}"));
        assertEquals(Verdict.INVALID, verifyWith("order_id,transid,a&b",
                "{\"order_id\":\"ORD-1\",\"transid\":\"TXN-1\",\"a&b\":\"x\"}"));
    }

    @Test
    void multiValuedHeadersWork() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        signed("2026-11-21T10:30:46+03:00").forEach((k, v) -> headers.put(k, List.of(v)));
        assertEquals(Verdict.VALID, verifier("2026-11-21T07:31:00Z", WebhookVerifier.DEFAULT_WINDOW).verifyMultiValued(headers, BODY));
    }

    @Test
    void verdictsCarryTheResponseTheSpecAsksFor() {
        assertEquals(200, Verdict.VALID.httpStatus());
        assertEquals(200, Verdict.UNSIGNED.httpStatus());
        assertEquals(200, Verdict.REPLAY.httpStatus());
        assertEquals(401, Verdict.INVALID.httpStatus());
        assertEquals(401, Verdict.STALE.httpStatus());
        assertTrue(Verdict.VALID.shouldProcess() && Verdict.UNSIGNED.shouldProcess());
        assertFalse(Verdict.REPLAY.shouldProcess() || Verdict.INVALID.shouldProcess() || Verdict.STALE.shouldProcess());
    }

    @Test
    void payloadParsingIsLenientAboutUnknownFieldsAndStrictAboutAmounts() {
        WebhookPayload payload = WebhookPayload.parse(BODY.replace("\"phone\"", "\"operator\":\"x\",\"phone\""));
        assertEquals("ORD-2001", payload.orderId());
        assertEquals("TXN-WLT-2001", payload.transId());
        assertEquals("000", payload.resultCode());
        assertEquals(io.github.selcomdevelopers.selcom.result.Status.SUCCESS, payload.claimedStatus());
        assertEquals(10000, payload.amountTzs().units());
        assertFalse(payload.toString().contains("255700000001"));
        assertThrows(IllegalArgumentException.class, () -> WebhookPayload.parse("{\"amount\":\"10000.5\"}").amountTzs());
        assertThrows(IllegalArgumentException.class, () -> WebhookPayload.parse("{}").amountTzs());
        assertThrows(IllegalArgumentException.class, () -> WebhookPayload.parse("nope"));
    }

    @Test
    void replayStoreForgetsAfterTheWindow() {
        InMemoryReplayStore store = new InMemoryReplayStore();
        Instant t0 = Instant.parse("2026-11-21T07:31:00Z");
        assertTrue(store.markIfNew("d1", t0, t0.plusSeconds(300)));
        assertFalse(store.markIfNew("d1", t0.plusSeconds(299), t0.plusSeconds(599)));
        assertTrue(store.markIfNew("d1", t0.plusSeconds(301), t0.plusSeconds(601)));
    }
}
