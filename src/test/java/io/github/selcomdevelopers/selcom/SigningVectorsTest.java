package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.signing.Hs256Signer;
import io.github.selcomdevelopers.selcom.signing.HttpMethod;
import io.github.selcomdevelopers.selcom.signing.RequestSigner;
import io.github.selcomdevelopers.selcom.signing.SignedRequest;
import io.github.selcomdevelopers.selcom.signing.SigningException;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Runs every case and every reject in vectors/signing.json against the pure signing function. */
class SigningVectorsTest {
    private static final JsonNode VECTORS = Vectors.read("signing.json");

    @Test
    void vectorFileHasTheCasesWeExpect() {
        assertTrue(VECTORS.get("cases").size() >= 20, "cases: " + VECTORS.get("cases").size());
        assertTrue(VECTORS.get("rejects").size() >= 11, "rejects: " + VECTORS.get("rejects").size());
    }

    @TestFactory
    Stream<DynamicTest> cases() {
        return StreamSupport.stream(VECTORS.get("cases").spliterator(), false).map(c -> dynamicTest(c.get("id").asText(), () -> runCase(c)));
    }

    @TestFactory
    Stream<DynamicTest> rejects() {
        return StreamSupport.stream(VECTORS.get("rejects").spliterator(), false).map(c -> dynamicTest(c.get("id").asText(), () -> runReject(c)));
    }

    private static void runCase(JsonNode c) {
        JsonNode expected = c.get("expected");
        HttpMethod method = HttpMethod.valueOf(c.get("method").asText());
        SignedRequest signed = RequestSigner.sign(method, Vectors.params(c.get("params")), c.get("timestamp").asText(),
                c.get("api_key").asText(), new Hs256Signer(c.get("secret").asText()));

        // flattened: ordered [key, value] pairs with JSON types
        JsonNode flattened = expected.get("flattened");
        assertEquals(flattened.size(), signed.flattened().size());
        for (int i = 0; i < flattened.size(); i++) {
            assertEquals(flattened.get(i).get(0).asText(), signed.flattened().get(i).key());
            JsonNode value = flattened.get(i).get(1);
            Object actual = signed.flattened().get(i).value();
            if (value.isIntegralNumber()) {
                assertEquals(value.longValue(), actual);
            } else if (value.isBoolean()) {
                assertEquals(value.booleanValue(), actual);
            } else {
                assertEquals(value.asText(), actual);
            }
        }
        assertEquals(expected.get("signed_fields").asText(), signed.signedFields());
        assertEquals(expected.get("signing_string").asText(), signed.signingString());
        assertEquals(expected.get("digest").asText(), signed.digest());
        assertEquals(expected.get("authorization").asText(), signed.authorization());
        if (expected.has("digest_method")) {
            assertEquals(expected.get("digest_method").asText(), signed.headers().get("Digest-Method"));
        }
        assertEquals(signed.authorization(), signed.headers().get("Authorization"));
        assertEquals(signed.digest(), signed.headers().get("Digest"));
        assertEquals(c.get("timestamp").asText(), signed.headers().get("Timestamp"));
        assertEquals(signed.signedFields(), signed.headers().get("Signed-Fields"));

        if (method.usesQuery()) {
            assertEquals(expected.get("query").asText(), signed.query());
            assertNull(signed.body());
        } else if (expected.has("body")) {
            JsonNode expectedBody = Json.parse(expected.get("body").asText());
            JsonNode actualBody = Json.parse(signed.body());
            assertTrue(Vectors.sameJson(expectedBody, actualBody), "expected " + expected.get("body").asText() + " but was " + signed.body());
            assertNull(signed.query());
        }
    }

    private static void runReject(JsonNode c) {
        SigningException e = assertThrows(SigningException.class, () -> {
            // The signer is built inside the call: an empty secret is itself a refusal.
            Hs256Signer signer = new Hs256Signer(c.get("secret").asText());
            RequestSigner.sign(HttpMethod.valueOf(c.get("method").asText()), Vectors.params(c.get("params")),
                    c.get("timestamp").asText(), c.get("api_key").asText(), signer);
        });
        assertEquals(c.get("error").asText(), e.code().name(), c.get("id").asText());
    }

    @Test
    void errorCodesInTheVectorsAreAllKnown() {
        List<String> known = java.util.Arrays.stream(SigningException.Code.values()).map(Enum::name).toList();
        VECTORS.get("rejects").forEach(r -> assertTrue(known.contains(r.get("error").asText()), r.get("error").asText()));
    }
}
