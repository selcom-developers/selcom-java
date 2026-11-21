package io.github.selcomdevelopers.selcom.signing;

import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.signing.SigningException.Code;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The pure signing function: no I/O and no clock. Given the parameters in the caller's order, a timestamp, the API key
 * and a {@link Signer}, it returns everything that goes on the wire. The algorithm is the one in the spec's
 * {@code docs/signing.md}.
 *
 * <p>Pass parameters in a {@link LinkedHashMap} (or any map with a stable order): the order is part of the signature.
 * Nested maps are flattened in place to dotted keys.
 *
 * <p>The steps are public so each can be used on its own: {@link #flatten}, {@link #normaliseValue},
 * {@link #signingString}, {@link #encodeQuery}, {@link #jsonBody}.
 */
public final class RequestSigner {
    /** The largest integer that every JSON implementation holds exactly: 2^53 - 1. */
    public static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*$");
    private static final BigInteger MAX_SAFE = BigInteger.valueOf(MAX_SAFE_INTEGER);
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private RequestSigner() {
    }

    /**
     * One flattened field.
     *
     * @param key the dotted key
     * @param value the normalised value: a {@link String}, a {@link Long} or a {@link Boolean}
     */
    public record Field(String key, Object value) {
    }

    /**
     * Signs a request. Fails closed: on any problem nothing is returned and nothing may be sent.
     *
     * @param method the HTTP method, which decides whether the result carries a query string or a body
     * @param params the parameters in send order; values may be strings, whole numbers, booleans, null, or nested maps
     * @param timestamp the timestamp, {@code yyyy-MM-ddTHH:mm:ss+HH:MM} (or {@code +HHMM}), used exactly as given
     * @param apiKey the API key
     * @param signer the digest algorithm
     * @return the signed request
     * @throws SigningException if the request cannot be signed
     */
    public static SignedRequest sign(HttpMethod method, Map<String, ?> params, String timestamp, String apiKey, Signer signer) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(signer, "signer");
        String authorization = authorization(apiKey);
        Timestamps.check(timestamp);
        List<Field> flat = flatten(params);
        String fields = signedFields(flat);
        String message = signingString(timestamp, flat);
        String digest = signer.sign(message);
        if (digest == null || digest.isEmpty()) {
            throw new SigningException(Code.EMPTY_DIGEST, "refusing to send an empty Digest");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Digest-Method", signer.method());
        headers.put("Digest", digest);
        headers.put("Timestamp", timestamp);
        headers.put("Signed-Fields", fields);
        return new SignedRequest(timestamp, flat, fields, message, digest, authorization, headers,
                method.usesQuery() ? encodeQuery(flat) : null,
                method.usesQuery() ? null : jsonBody(flat));
    }

    /**
     * Builds the {@code Authorization} header value: {@code SELCOM } and the base64 of the API key alone.
     *
     * @param apiKey the API key
     * @return the header value
     * @throws SigningException if the key is empty
     */
    public static String authorization(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new SigningException(Code.EMPTY_API_KEY, "refusing to build Authorization from an empty key");
        }
        return "SELCOM " + Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Flattens nested maps to dotted keys in place, keeping order, and normalises every value.
     *
     * @param params the parameters
     * @return the flattened fields
     * @throws SigningException if a key or value breaks the rules, or there is nothing to sign
     */
    public static List<Field> flatten(Map<String, ?> params) {
        Objects.requireNonNull(params, "params");
        List<Field> flat = new ArrayList<>();
        walk("", params, flat, new HashSet<>());
        if (flat.isEmpty()) {
            throw new SigningException(Code.EMPTY_PARAMS, "there is nothing to sign");
        }
        return flat;
    }

    private static void walk(String prefix, Map<?, ?> object, List<Field> out, Set<String> seen) {
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            Object rawKey = entry.getKey();
            if (!(rawKey instanceof String name) || name.isEmpty()) {
                throw new SigningException(Code.INVALID_KEY,
                        "empty or non-string key under " + (prefix.isEmpty() ? "root" : prefix));
            }
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            if (!KEY.matcher(key).matches()) {
                throw new SigningException(Code.INVALID_KEY, "'" + key + "' has characters outside [A-Za-z0-9_.-]");
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                if (nested.isEmpty()) {
                    throw new SigningException(Code.EMPTY_OBJECT, key + " is an empty object; omit it");
                }
                walk(key, nested, out, seen);
                continue;
            }
            if (!seen.add(key)) {
                throw new SigningException(Code.DUPLICATE_KEY, key + " appears twice after flattening");
            }
            out.add(new Field(key, normaliseValue(key, value)));
        }
    }

    /**
     * Returns the scalar that is both signed and sent for one field.
     *
     * @param key the field's key, for error messages
     * @param value the value
     * @return a {@link String}, a {@link Long} or a {@link Boolean}; null becomes the empty string
     * @throws SigningException for fractions, integers beyond 2^53 - 1, and types with no flat form
     */
    public static Object normaliseValue(String key, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return checkRange(key, BigInteger.valueOf(((Number) value).longValue()));
        }
        if (value instanceof BigInteger big) {
            return checkRange(key, big);
        }
        if (value instanceof BigDecimal decimal) {
            return fromDecimal(key, decimal);
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isFinite(d)) {
                throw new SigningException(Code.NON_INTEGER_NUMBER, key + ": not a finite whole number");
            }
            return fromDecimal(key, new BigDecimal(d));
        }
        throw new SigningException(Code.UNSUPPORTED_TYPE, key + ": " + value.getClass().getSimpleName() + " values cannot be signed");
    }

    private static Long fromDecimal(String key, BigDecimal decimal) {
        // Bound the work: strip trailing zeros, then decide from scale and precision before any conversion.
        BigDecimal stripped = decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
        if (stripped.scale() > 0) {
            throw new SigningException(Code.NON_INTEGER_NUMBER, key + ": value has a fractional part");
        }
        // Digits before the decimal point; 2^53 - 1 has 16.
        if ((long) stripped.precision() - stripped.scale() > 16) {
            throw new SigningException(Code.NUMBER_OUT_OF_RANGE, key + ": send integers above 2^53-1 as strings");
        }
        return checkRange(key, BigInteger.valueOf(stripped.longValueExact()));
    }

    private static Long checkRange(String key, BigInteger value) {
        if (value.abs().compareTo(MAX_SAFE) > 0) {
            throw new SigningException(Code.NUMBER_OUT_OF_RANGE, key + ": send integers above 2^53-1 as strings");
        }
        return value.longValue();
    }

    /**
     * Text form of a normalised value inside the signing string.
     *
     * @param value a {@link String}, {@link Long} or {@link Boolean}
     * @return the text
     */
    public static String formatValue(Object value) {
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Long || value instanceof String) {
            return value.toString();
        }
        throw new SigningException(Code.UNSUPPORTED_TYPE, "not a normalised value");
    }

    /**
     * The {@code Signed-Fields} value: the keys in order, joined with commas and no spaces.
     *
     * @param flat the flattened fields
     * @return the header value
     */
    public static String signedFields(List<Field> flat) {
        StringBuilder out = new StringBuilder();
        for (Field field : flat) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(field.key());
        }
        return out.toString();
    }

    /**
     * Builds the signing string: {@code timestamp=} then {@code &key=value} for each field, values raw.
     *
     * @param timestamp the timestamp
     * @param flat the flattened fields
     * @return the text to sign
     */
    public static String signingString(String timestamp, List<Field> flat) {
        StringBuilder out = new StringBuilder("timestamp=").append(timestamp);
        for (Field field : flat) {
            out.append('&').append(field.key()).append('=').append(formatValue(field.value()));
        }
        return out.toString();
    }

    /**
     * Builds the query string: RFC 3986 percent-encoding of UTF-8, so a space is {@code %20} and {@code +} is
     * {@code %2B}. The signature covers the decoded values.
     *
     * @param flat the flattened fields
     * @return the query string without the leading question mark
     */
    public static String encodeQuery(List<Field> flat) {
        StringBuilder out = new StringBuilder();
        for (Field field : flat) {
            if (out.length() > 0) {
                out.append('&');
            }
            percentEncode(out, field.key());
            out.append('=');
            percentEncode(out, formatValue(field.value()));
        }
        return out.toString();
    }

    private static void percentEncode(StringBuilder out, String text) {
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
    }

    /**
     * Builds the POST body: a compact JSON object with the flattened keys in order and the normalised values.
     *
     * @param flat the flattened fields
     * @return the JSON text
     */
    public static String jsonBody(List<Field> flat) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Field field : flat) {
            body.put(field.key(), field.value());
        }
        return Json.write(body);
    }
}
