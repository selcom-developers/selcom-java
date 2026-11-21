package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.selcomdevelopers.selcom.internal.Json;
import io.github.selcomdevelopers.selcom.redact.Redactor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedactorTest {
    private final Redactor redactor = Redactor.defaults();

    @Test
    void secretsAreRedactedWhateverTheCase() {
        for (String field : new String[] {"pin", "PIN", "secret", "api_secret", "Authorization", "Digest", "card_token"}) {
            assertEquals("[REDACTED]", redactor.redactValue(field, "anything"), field);
        }
        assertEquals("HS256", redactor.redactValue("Digest-Method", "HS256"));
    }

    @Test
    void phoneEmailAndNameFormsMatchTheSpecExamples() {
        assertEquals("********0001", redactor.redactValue("msisdn", "255700000001"));
        assertEquals("********0001", redactor.redactValue("buyer_phone", "255700000001"));
        assertEquals("********0001", redactor.redactValue("billing.phone", "255700000001"));
        assertEquals("t***@example.com", redactor.redactValue("buyer_email", "test.buyer@example.com"));
        assertEquals("T***", redactor.redactValue("buyer_name", "Test Buyer"));
        assertEquals("T***", redactor.redactValue("shipping.firstname", "Test"));
        assertEquals("[REDACTED]", redactor.redactValue("email", "not-an-email"));
        assertEquals("", redactor.redactValue("name", ""));
        assertEquals("***", redactor.redactValue("phone", "123"));
        assertEquals("ORD-1001", redactor.redactValue("order_id", "ORD-1001"));
    }

    @Test
    void utilityRefIsAPhoneOnlyOnWalletCashIn() {
        assertEquals("01234567890", redactor.redactValue("utilityref", "01234567890"));
        assertEquals("*******7890", Redactor.treatingUtilityRefAsPhone().redactValue("utilityref", "01234567890"));
    }

    @Test
    void headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "SELCOM abc");
        headers.put("Digest", "xyz=");
        headers.put("Digest-Method", "HS256");
        headers.put("Timestamp", "2026-11-21T10:30:46+03:00");
        Map<String, String> out = redactor.redactHeaders(headers);
        assertEquals("[REDACTED]", out.get("Authorization"));
        assertEquals("[REDACTED]", out.get("Digest"));
        assertEquals("HS256", out.get("Digest-Method"));
        assertEquals("2026-11-21T10:30:46+03:00", out.get("Timestamp"));
    }

    @Test
    void flatAndNestedJson() {
        String flat = "{\"vendor\":\"TILL123\",\"pin\":\"4321\",\"msisdn\":255700000001,\"billing.firstname\":\"John\",\"amount\":8000}";
        JsonNode out = Json.parse(redactor.redactJson(flat));
        assertEquals("TILL123", out.get("vendor").asText());
        assertEquals("[REDACTED]", out.get("pin").asText());
        assertEquals("********0001", out.get("msisdn").asText());
        assertEquals("J***", out.get("billing.firstname").asText());
        assertEquals(8000, out.get("amount").asLong());

        String nested = "{\"data\":[{\"name\":\"Test Buyer\",\"card_token\":\"tok\",\"masked_card\":\"4111********1111\"}],\"billing\":{\"phone\":\"255700000001\",\"email\":\"a@b.example\"}}";
        JsonNode n = Json.parse(redactor.redactJson(nested));
        assertEquals("T***", n.get("data").get(0).get("name").asText());
        assertEquals("[REDACTED]", n.get("data").get(0).get("card_token").asText());
        assertEquals("4111********1111", n.get("data").get(0).get("masked_card").asText());
        assertEquals("********0001", n.get("billing").get("phone").asText());
        assertEquals("a***@b.example", n.get("billing").get("email").asText());
    }

    @Test
    void anObjectUnderASensitiveKeyIsRedactedWhole() {
        JsonNode n = Json.parse(redactor.redactJson("{\"pin\":{\"digits\":\"4321\"},\"name\":[\"Test\"]}"));
        assertEquals("[REDACTED]", n.get("pin").asText());
        assertEquals("[REDACTED]", n.get("name").asText());
    }

    @Test
    void textThatIsNotJsonIsNeverEchoed() {
        assertEquals("[REDACTED]", redactor.redactJson("pin=4321 <html>"));
        assertEquals("[REDACTED]", redactor.redactJson(null));
    }

    @Test
    void queryStrings() {
        String out = redactor.redactQuery("utilitycode=LUKU&msisdn=255700000001&buyer_email=t%40example.com&pin=4321&transid=TXN-1&flag");
        assertEquals("utilitycode=LUKU&msisdn=********0001&buyer_email=t***@example.com&pin=[REDACTED]&transid=TXN-1&flag", out);
        assertEquals("utilityref=*******7890&order_id=ORD-1",
                Redactor.treatingUtilityRefAsPhone().redactQuery("utilityref=01234567890&order_id=ORD-1"));
    }

    @Test
    void maps() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("vendor", "TILL123");
        params.put("pin", "4321");
        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("firstname", "John");
        billing.put("phone", "255700000001");
        params.put("billing", billing);
        params.put("amount", 8000L);
        Map<String, Object> out = redactor.redactMap(params);
        assertEquals("[REDACTED]", out.get("pin"));
        assertEquals(8000L, out.get("amount"));
        String text = out.toString();
        assertFalse(text.contains("John") || text.contains("255700000001") || text.contains("4321"), text);
        assertTrue(text.contains("J***"));
    }
}
