package io.github.selcomdevelopers.selcom.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared JSON helpers. Internal: not part of the supported API, and may change without notice.
 *
 * <p>Floating-point JSON numbers are read as {@link java.math.BigDecimal} so that {@code 8000.0} keeps its scale and
 * can be checked for a fractional part exactly.
 */
public final class Json {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private Json() {
    }

    /**
     * The shared mapper. Do not reconfigure it.
     *
     * @return the mapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Parses text as JSON.
     *
     * @param text the text, possibly null or not JSON
     * @return the parsed tree, or null if the text is null, blank or not valid JSON
     */
    public static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            return node == null || node.isMissingNode() ? null : node;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Writes a value as compact JSON.
     *
     * @param value the value
     * @return the JSON text
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise JSON", e);
        }
    }

    /**
     * Reads a scalar field as text.
     *
     * @param object a JSON object, or null
     * @param field the field name
     * @return the text of a string, number or boolean field; null if absent, null or not a scalar
     */
    public static String text(JsonNode object, String field) {
        if (object == null || !object.isObject()) {
            return null;
        }
        JsonNode node = object.get(field);
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        return node.asText();
    }
}
