package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.webhook.InMemoryReplayStore;
import io.github.selcomdevelopers.selcom.webhook.Verdict;
import io.github.selcomdevelopers.selcom.webhook.WebhookPayload;
import io.github.selcomdevelopers.selcom.webhook.WebhookVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Runs every sample in vectors/responses/checkout.webhook.json, in order, through one replay store. */
class WebhookVectorsTest {
    private static final JsonNode FILE = Vectors.read("responses/checkout.webhook.json");

    @Test
    void sampleCount() {
        assertTrue(FILE.get("samples").size() >= 10, "samples: " + FILE.get("samples").size());
    }

    @TestFactory
    Stream<DynamicTest> samplesInOrder() {
        JsonNode verification = FILE.get("verification");
        Clock clock = Clock.fixed(OffsetDateTime.parse(verification.get("now").asText()).toInstant(), ZoneOffset.UTC);
        // One verifier, so one replay store, for all samples.
        WebhookVerifier verifier = WebhookVerifier.hs256(verification.get("secret").asText(), clock,
                Duration.ofSeconds(verification.get("window_seconds").asLong()), new InMemoryReplayStore());
        List<DynamicTest> tests = new ArrayList<>();
        // Run in order inside one test per sample, but evaluate lazily so the replay store sees them in order.
        for (JsonNode sample : FILE.get("samples")) {
            tests.add(dynamicTest(sample.get("name").asText(), () -> {
                Map<String, String> headers = new LinkedHashMap<>();
                sample.get("headers").properties().forEach(e -> headers.put(e.getKey(), e.getValue().asText()));
                String rawBody = sample.get("raw_body").asText();
                assertEquals(Verdict.valueOf(sample.get("expected_verification").asText()), verifier.verify(headers, rawBody));
                assertEquals(sample.get("expected_amount_tzs").asLong(), WebhookPayload.parse(rawBody).amountTzs().units());
            }));
        }
        return tests.stream();
    }
}
