package io.github.selcomdevelopers.selcom;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.internal.Json;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Loads the copy of the shared spec vectors from src/test/resources/vectors. */
final class Vectors {
    private Vectors() {
    }

    static JsonNode read(String resource) {
        try (InputStream in = Vectors.class.getResourceAsStream("/vectors/" + resource)) {
            if (in == null) {
                throw new IllegalStateException("missing vector file " + resource);
            }
            return Json.mapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** All response fixtures except the webhook samples, ordered by file name. */
    static List<JsonNode> responseFiles() {
        try {
            Path dir = Paths.get(Vectors.class.getResource("/vectors/responses").toURI());
            List<JsonNode> out = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.sorted().toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".json") && !name.equals("checkout.webhook.json")) {
                        out.add(read("responses/" + name));
                    }
                }
            }
            return out;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Converts parsed JSON to the Java values the signer takes. Objects keep their order. */
    static Object toJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                map.put(entry.getKey(), toJava(entry.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(element -> list.add(toJava(element)));
            return list;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? (Object) node.longValue() : (Object) node.bigIntegerValue();
        }
        return node.decimalValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> params(JsonNode node) {
        return (Map<String, Object>) toJava(node);
    }

    /** Same keys in the same order and the same values and JSON types. Numbers compare by value. */
    static boolean sameJson(JsonNode expected, JsonNode actual) {
        if (expected.isObject() && actual.isObject()) {
            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            expected.fieldNames().forEachRemaining(a::add);
            actual.fieldNames().forEachRemaining(b::add);
            if (!a.equals(b)) {
                return false;
            }
            return a.stream().allMatch(key -> sameJson(expected.get(key), actual.get(key)));
        }
        if (expected.isIntegralNumber() && actual.isIntegralNumber()) {
            return expected.longValue() == actual.longValue();
        }
        return expected.equals(actual);
    }
}
