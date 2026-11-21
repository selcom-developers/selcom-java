package io.github.selcomdevelopers.selcom.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.signing.Hs256Signer;
import io.github.selcomdevelopers.selcom.signing.RequestSigner;
import io.github.selcomdevelopers.selcom.signing.Signer;
import io.github.selcomdevelopers.selcom.signing.SigningException;
import io.github.selcomdevelopers.selcom.signing.Timestamps;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Verifies checkout webhook deliveries, as described in the spec's {@code docs/webhooks.md}.
 *
 * <p><strong>A webhook is a hint, not proof.</strong> Never fulfil an order because a webhook said so: confirm with
 * {@code orderStatus} first, every time. {@link Verdict#VALID} vouches only for the fields named in {@code Signed-Fields};
 * anything else in the body, the amount included, may have been changed in transit.
 *
 * <p>Read the raw body before any framework parses it, and pass it unchanged. The verifier is thread-safe if its
 * {@link ReplayStore} is.
 */
public final class WebhookVerifier {
    /** The default window around the current time: 300 seconds, in either direction. */
    public static final Duration DEFAULT_WINDOW = Duration.ofSeconds(300);

    private final Signer signer;
    private final Clock clock;
    private final Duration window;
    private final ReplayStore replayStore;

    /**
     * Creates a verifier.
     *
     * @param signer the digest algorithm; its {@code method()} must match the {@code Digest-Method} header
     * @param clock the clock, injectable so tests can fix it
     * @param window how far the timestamp may be from the current time, in either direction
     * @param replayStore remembers digests already seen
     */
    public WebhookVerifier(Signer signer, Clock clock, Duration window, ReplayStore replayStore) {
        this.signer = Objects.requireNonNull(signer, "signer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
        this.replayStore = Objects.requireNonNull(replayStore, "replayStore");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    /**
     * Creates a verifier for HS256 with the system clock, a 300 second window and an in-memory replay store.
     *
     * @param secret the API secret the gateway signs webhooks with (which key that is remains to be confirmed in the sandbox)
     * @return the verifier
     * @throws SigningException if the secret is empty
     */
    public static WebhookVerifier hs256(String secret) {
        return new WebhookVerifier(new Hs256Signer(secret), Clock.systemUTC(), DEFAULT_WINDOW, new InMemoryReplayStore());
    }

    /**
     * Creates a verifier for HS256 with your clock, window and replay store.
     *
     * @param secret the API secret
     * @param clock the clock
     * @param window the window
     * @param replayStore the replay store
     * @return the verifier
     * @throws SigningException if the secret is empty
     */
    public static WebhookVerifier hs256(String secret, Clock clock, Duration window, ReplayStore replayStore) {
        return new WebhookVerifier(new Hs256Signer(secret), clock, window, replayStore);
    }

    /**
     * Verifies one delivery from headers with a single value each (header names are case-insensitive).
     *
     * @param headers the request headers
     * @param rawBody the body exactly as received
     * @return the verdict
     */
    public Verdict verify(Map<String, String> headers, String rawBody) {
        return verifyInternal(headers, rawBody);
    }

    /**
     * Verifies one delivery from multi-valued headers, as most servers expose them. The first value of each is used.
     *
     * @param headers the request headers (header names are case-insensitive)
     * @param rawBody the body exactly as received
     * @return the verdict
     */
    public Verdict verifyMultiValued(Map<String, List<String>> headers, String rawBody) {
        Map<String, String> first = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (name != null && values != null && !values.isEmpty()) {
                first.put(name, values.get(0));
            }
        });
        return verify(first, rawBody);
    }

    private Verdict verifyInternal(Map<String, String> headers, String rawBody) {
        try {
            return check(headers, rawBody);
        } catch (RuntimeException e) {
            // Untrusted input must never make verify() throw.
            return Verdict.INVALID;
        }
    }

    private Verdict check(Map<String, String> headers, String rawBody) {
        Map<String, String> lower = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                lower.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        String digest = lower.get("digest");
        if (digest == null) {
            return Verdict.UNSIGNED;
        }
        if (digest.isEmpty() || !signer.method().equals(lower.get("digest-method"))) {
            return Verdict.INVALID;
        }
        String timestamp = lower.getOrDefault("timestamp", "");
        String fieldsHeader = lower.getOrDefault("signed-fields", "");
        OffsetDateTime time;
        JsonNode body;
        try {
            time = Timestamps.parse(timestamp);
        } catch (SigningException e) {
            return Verdict.INVALID;
        }
        body = Json.parse(rawBody);
        if (body == null || !body.isObject() || fieldsHeader.isEmpty()) {
            return Verdict.INVALID;
        }
        String[] names = fieldsHeader.split(",", -1);
        // The signed list must cover the delivery's identity.
        if (!List.of(names).contains("order_id") || !List.of(names).contains("transid")) {
            return Verdict.INVALID;
        }
        List<RequestSigner.Field> flat = new ArrayList<>();
        for (String field : names) {
            JsonNode found = lookup(body, field);
            if (found == null) {
                return Verdict.INVALID;
            }
            Object value;
            try {
                value = RequestSigner.normaliseValue(field, toJava(found));
            } catch (SigningException e) {
                return Verdict.INVALID;
            }
            // '&' separates fields in the signing string, so it may not appear in a signed name or value.
            if (field.indexOf('&') >= 0 || RequestSigner.formatValue(value).indexOf('&') >= 0) {
                return Verdict.INVALID;
            }
            flat.add(new RequestSigner.Field(field, value));
        }
        String expected = signer.sign(RequestSigner.signingString(timestamp, flat));
        // Constant-time comparison.
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), digest.getBytes(StandardCharsets.UTF_8))) {
            return Verdict.INVALID;
        }
        Instant now = clock.instant();
        if (Duration.between(time.toInstant(), now).abs().compareTo(window) > 0) {
            return Verdict.STALE;
        }
        // Remember the digest until the window around the signed time has passed, so a late replay is still caught.
        Instant signed = time.toInstant();
        Instant expiry = (signed.isAfter(now) ? signed : now).plus(window);
        if (!replayStore.markIfNew(digest, now, expiry)) {
            return Verdict.REPLAY;
        }
        return Verdict.VALID;
    }

    /** A flat key first, then a dotted name walked through nested objects. Returns null when absent. */
    private static JsonNode lookup(JsonNode body, String field) {
        if (body.has(field)) {
            return body.get(field);
        }
        JsonNode node = body;
        for (String part : field.split("\\.", -1)) {
            if (!node.isObject() || !node.has(part)) {
                return null;
            }
            node = node.get(part);
        }
        return node;
    }

    private static Object toJava(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.bigIntegerValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node; // an object or array: normaliseValue rejects it as UNSUPPORTED_TYPE
    }
}
