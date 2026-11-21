package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.result.InvalidResponseException;
import io.github.selcomdevelopers.selcom.result.Result;
import io.github.selcomdevelopers.selcom.result.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Feeds every scenario in vectors/responses/ (except webhooks) through the client with a fake transport, once through the
 * typed method and once through the raw {@code execute}, and checks the typed status or the raised error.
 */
class ResponseVectorsTest {
    private static final List<JsonNode> FILES = Vectors.responseFiles();

    @Test
    void everyV1OperationHasAFixtureFile() {
        List<String> names = FILES.stream().map(f -> f.get("operation").asText()).toList();
        for (Operation operation : Operation.values()) {
            assertTrue(names.contains(operation.operationName()), "no fixture for " + operation.operationName());
        }
        assertEquals(Operation.values().length, FILES.size());
    }

    @Test
    void operationTableMatchesTheFixtureHeaders() {
        for (JsonNode file : FILES) {
            Operation operation = Operation.byName(file.get("operation").asText()).orElseThrow();
            assertEquals(file.get("method").asText(), operation.method().name(), operation.operationName());
            assertEquals(file.get("path").asText(), operation.path(), operation.operationName());
            assertEquals(file.get("kind").asText(), operation.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                    operation.operationName());
        }
    }

    @TestFactory
    Stream<DynamicTest> scenarios() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode file : FILES) {
            Operation operation = Operation.byName(file.get("operation").asText()).orElseThrow();
            for (JsonNode scenario : file.get("scenarios")) {
                tests.add(dynamicTest(operation.operationName() + " / " + scenario.get("name").asText(),
                        () -> run(operation, scenario)));
            }
        }
        return tests.stream();
    }

    private static void run(Operation operation, JsonNode scenario) {
        int httpStatus = scenario.get("http_status").asInt();
        String raw = scenario.has("raw_body") ? scenario.get("raw_body").asText() : scenario.get("body").toString();

        // Typed method.
        FakeTransport typedTransport = FakeTransport.replying(httpStatus, raw);
        SelcomClient client = Samples.client(typedTransport);
        // Raw execute with parameters we build ourselves.
        FakeTransport rawTransport = FakeTransport.replying(httpStatus, raw);
        SelcomClient rawClient = Samples.client(rawTransport);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("order_id", "ORD-1001");

        if (scenario.has("expected_error")) {
            assertEquals("INVALID_RESPONSE", scenario.get("expected_error").asText());
            InvalidResponseException typed = assertThrows(InvalidResponseException.class, () -> Samples.call(client, operation));
            assertEquals(httpStatus, typed.httpStatus());
            assertEquals(raw, typed.rawBody());
            assertThrows(InvalidResponseException.class, () -> rawClient.execute(operation, params));
        } else {
            Status expected = Status.valueOf(scenario.get("expected_status").asText());
            Result<?> typed = Samples.call(client, operation);
            assertEquals(expected, typed.status());
            assertEquals(httpStatus, typed.httpStatus());
            assertEquals(raw, typed.raw());
            assertEquals(expected.isFinal(), typed.isFinal());
            assertEquals(expected, rawClient.execute(operation, params).status());
        }

        // The right request went out.
        var sent = typedTransport.last();
        assertEquals(operation.method(), sent.method());
        assertEquals(operation.path(), sent.uri().getPath());
        assertEquals("apigwtest.selcommobile.com", sent.uri().getHost());
        assertFalse(sent.headers().get("Digest").isEmpty());
    }
}
