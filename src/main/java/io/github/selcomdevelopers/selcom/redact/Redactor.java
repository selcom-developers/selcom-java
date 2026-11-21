package io.github.selcomdevelopers.selcom.redact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.selcomdevelopers.selcom.internal.Json;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redacts what must not reach a log: secrets, phone numbers, e-mail addresses and names. Works on headers, flat or
 * nested JSON bodies, maps and query strings. Use it for anything you store or log.
 *
 * <table class="striped">
 * <caption>Rules</caption>
 * <tr><th>Data</th><th>Fields (also their {@code billing.} and {@code shipping.} forms)</th><th>Redacted form</th></tr>
 * <tr><td>Secrets</td><td>pin, secret, api_secret, Authorization, Digest, card_token</td><td>{@code [REDACTED]}</td></tr>
 * <tr><td>Phone numbers</td><td>msisdn, phone, buyer_phone, and utilityref on wallet cash-in</td>
 *     <td>all but the last 4 characters replaced: {@code ********0001}</td></tr>
 * <tr><td>E-mail</td><td>buyer_email, email</td><td>{@code t***@example.com}</td></tr>
 * <tr><td>Names</td><td>buyer_name, firstname, lastname, name</td><td>{@code T***}</td></tr>
 * </table>
 *
 * <p>{@code utilityref} is a phone number only on wallet cash-in (on a utility payment it is a meter or control
 * number), so it is redacted only by an instance made with {@link #treatingUtilityRefAsPhone()}.
 */
public final class Redactor {
    /** The replacement for secrets. */
    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SECRETS = Set.of("pin", "secret", "api_secret", "authorization", "digest", "card_token");
    private static final Set<String> PHONES = Set.of("msisdn", "phone", "buyer_phone");
    private static final Set<String> EMAILS = Set.of("buyer_email", "email");
    private static final Set<String> NAMES = Set.of("buyer_name", "firstname", "lastname", "name");
    private static final Redactor DEFAULTS = new Redactor(false);
    private static final Redactor WALLET = new Redactor(true);

    private final boolean utilityRefIsPhone;

    private Redactor(boolean utilityRefIsPhone) {
        this.utilityRefIsPhone = utilityRefIsPhone;
    }

    /**
     * The standard rules, with {@code utilityref} left alone.
     *
     * @return a redactor
     */
    public static Redactor defaults() {
        return DEFAULTS;
    }

    /**
     * The standard rules, plus {@code utilityref} redacted as a phone number. Use for wallet cash-in.
     *
     * @return a redactor
     */
    public static Redactor treatingUtilityRefAsPhone() {
        return WALLET;
    }

    /**
     * Redacts one value according to its field name.
     *
     * @param field the field name, flat or dotted (for example {@code billing.phone}) or a header name
     * @param value the value; null stays null
     * @return the redacted value, or the value itself if the field is not sensitive
     */
    public String redactValue(String field, String value) {
        if (value == null) {
            return null;
        }
        String name = lastSegment(field);
        if (SECRETS.contains(name)) {
            return REDACTED;
        }
        if (PHONES.contains(name) || (utilityRefIsPhone && name.equals("utilityref"))) {
            return maskPhone(value);
        }
        if (EMAILS.contains(name)) {
            return maskEmail(value);
        }
        if (NAMES.contains(name)) {
            return maskName(value);
        }
        return value;
    }

    private boolean sensitive(String field) {
        String name = lastSegment(field);
        return SECRETS.contains(name) || PHONES.contains(name) || EMAILS.contains(name) || NAMES.contains(name)
                || (utilityRefIsPhone && name.equals("utilityref"));
    }

    private static String lastSegment(String field) {
        String lower = field.toLowerCase(Locale.ROOT);
        return lower.substring(lower.lastIndexOf('.') + 1);
    }

    private static String maskPhone(String value) {
        int keep = Math.min(4, value.length());
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at < 1) {
            return REDACTED;
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String maskName(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return new String(Character.toChars(value.codePointAt(0))) + "***";
    }

    /**
     * Redacts headers: Authorization and Digest become {@code [REDACTED]}.
     *
     * @param headers the headers
     * @return a copy, in the same order
     */
    public Map<String, String> redactHeaders(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, value) -> out.put(name, redactValue(name, value)));
        return out;
    }

    /**
     * Redacts a query string.
     *
     * @param rawQuery the query without the question mark, as sent
     * @return the query with sensitive values replaced (the replacement is not percent-encoded)
     */
    public String redactQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return rawQuery;
        }
        StringBuilder out = new StringBuilder();
        for (String pair : rawQuery.split("&", -1)) {
            if (out.length() > 0) {
                out.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.append(pair);
                continue;
            }
            String rawKey = pair.substring(0, eq);
            String key = decode(rawKey);
            if (sensitive(key)) {
                out.append(rawKey).append('=').append(redactValue(key, decode(pair.substring(eq + 1))));
            } else {
                out.append(pair);
            }
        }
        return out.toString();
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    /**
     * Redacts a map such as the parameters of a request, nested maps included.
     *
     * @param params the map
     * @return a copy, in the same order
     */
    public Map<String, Object> redactMap(Map<String, ?> params) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            out.put(entry.getKey(), redactEntry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private Object redactEntry(String key, Object value) {
        if (value instanceof Map<?, ?> nested && !sensitive(key)) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                String child = String.valueOf(entry.getKey());
                out.put(child, redactEntry(key + "." + child, entry.getValue()));
            }
            return out;
        }
        if (value == null || !sensitive(key)) {
            return value;
        }
        if (value instanceof Map<?, ?>) {
            return REDACTED;
        }
        return redactValue(key, String.valueOf(value));
    }

    /**
     * Redacts a JSON body, flat or nested.
     *
     * @param json the JSON text
     * @return the redacted JSON, compact; if the text is not JSON, a placeholder, never the original text
     */
    public String redactJson(String json) {
        JsonNode tree = Json.parse(json);
        if (tree == null) {
            return REDACTED;
        }
        return Json.write(redactNode("", tree));
    }

    private JsonNode redactNode(String key, JsonNode node) {
        if (node.isContainerNode() && !key.isEmpty() && sensitive(key)) {
            return Json.mapper().getNodeFactory().textNode(REDACTED);
        }
        if (node.isObject()) {
            ObjectNode out = Json.mapper().createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                out.set(field.getKey(), redactNode(key.isEmpty() ? field.getKey() : key + "." + field.getKey(), field.getValue()));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = Json.mapper().createArrayNode();
            for (JsonNode element : node) {
                out.add(redactNode(key, element));
            }
            return out;
        }
        if (node.isNull() || key.isEmpty() || !sensitive(key)) {
            return node;
        }
        return Json.mapper().getNodeFactory().textNode(redactValue(key, node.asText()));
    }
}
