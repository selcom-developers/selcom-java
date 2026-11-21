package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.selcomdevelopers.selcom.api.Requests;
import io.github.selcomdevelopers.selcom.signing.Hs256Signer;
import io.github.selcomdevelopers.selcom.signing.SigningException;
import io.github.selcomdevelopers.selcom.signing.Timestamps;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigAndMoneyTest {
    private static SelcomConfig.Builder valid() {
        return SelcomConfig.builder().credentials("test-key-0001", "test-secret-0001").vendor("TILL123");
    }

    @Test
    void thereIsNoDefaultEnvironment() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> valid().build());
        assertTrue(e.getMessage().contains("no environment"), e.getMessage());
    }

    @Test
    void environmentsHaveTheSpecBaseUrls() {
        assertEquals("https://apigwtest.selcommobile.com", valid().environment(Environment.SANDBOX).build().baseUrl().toString());
        assertEquals("https://apigw.selcommobile.com", valid().environment(Environment.PRODUCTION).build().baseUrl().toString());
    }

    @Test
    void aCustomBaseUrlIsItsOwnChoiceAndNeverOverridesAPreset() {
        assertThrows(ConfigurationException.class, () -> valid().environment(Environment.SANDBOX).customBaseUrl("https://proxy.example.com").build());
        SelcomConfig custom = valid().customBaseUrl("https://proxy.example.com/gw/").build();
        assertEquals("https://proxy.example.com/gw", custom.baseUrl().toString());
        assertEquals(null, custom.environment());
        assertEquals("http://localhost:8080", valid().customBaseUrl("http://localhost:8080").build().baseUrl().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://proxy.example.com", "ftp://x.example.com", "not a url", "https://user:pw@x.example.com", "https://x.example.com/?a=b", "/relative"})
    void badCustomBaseUrlsAreRejected(String url) {
        assertThrows(ConfigurationException.class, () -> valid().customBaseUrl(url).build());
    }

    @Test
    void missingKeySecretOrVendorFailsAtConstruction() {
        assertThrows(ConfigurationException.class, () -> SelcomConfig.builder().environment(Environment.SANDBOX).vendor("TILL123").build());
        assertThrows(ConfigurationException.class, () -> SelcomConfig.builder().environment(Environment.SANDBOX).credentials("k", "").vendor("v").build());
        assertThrows(ConfigurationException.class, () -> SelcomConfig.builder().environment(Environment.SANDBOX).credentials("", "s").vendor("v").build());
        assertThrows(ConfigurationException.class, () -> SelcomConfig.builder().environment(Environment.SANDBOX).credentials("k", "s").build());
        assertThrows(SigningException.class, () -> new Hs256Signer(""));
    }

    @Test
    void theSecretAndPinNeverAppearInToString() {
        SelcomConfig config = valid().environment(Environment.SANDBOX).pin("4321")
                .checkoutCredentials("test-key-0002", "test-secret-0002").build();
        String text = config + " " + config.credentials() + " " + new Hs256Signer("test-secret-0001")
                + " " + new io.github.selcomdevelopers.selcom.api.Defaults("TILL123", "4321");
        assertFalse(text.contains("test-secret"), text);
        assertFalse(text.contains("4321"), text);
        assertFalse(text.contains("test-key-0001"), "the API key is masked: " + text);
        assertTrue(text.contains("****0001"), text);
        assertFalse(text.contains("test-key-0002"), text);
        assertTrue(text.contains("[REDACTED]"));
    }

    @Test
    void timeoutDefaultsAreTheSpecDefaultsAndAreConfigurable() {
        SelcomConfig config = valid().environment(Environment.SANDBOX).build();
        assertEquals(Duration.ofSeconds(10), config.timeoutsFor(OperationKind.OTHER).connect());
        assertEquals(Duration.ofSeconds(10), config.timeoutsFor(OperationKind.ORDER_STATUS).read());
        assertEquals(Duration.ofSeconds(10), config.timeoutsFor(OperationKind.STATUS_QUERY).read());
        assertEquals(Duration.ofSeconds(15), config.timeoutsFor(OperationKind.PAYMENT).connect());
        assertEquals(Duration.ofSeconds(180), config.timeoutsFor(OperationKind.PAYMENT).read());
        SelcomConfig custom = valid().environment(Environment.SANDBOX).paymentTimeouts(new Timeouts(Duration.ofSeconds(5), Duration.ofSeconds(240))).build();
        assertEquals(Duration.ofSeconds(240), custom.timeoutsFor(OperationKind.PAYMENT).read());
        assertThrows(ConfigurationException.class, () -> new Timeouts(Duration.ZERO, Duration.ofSeconds(1)));
    }

    @Test
    void operationTableCoversTheSixteenOperationsAndTheirKinds() {
        assertEquals(16, Operation.values().length);
        assertEquals(OperationKind.PAYMENT, Operation.CHECKOUT_WALLET_PAYMENT.kind());
        assertEquals(OperationKind.STATUS_QUERY, Operation.WALLETCASHIN_QUERY.kind());
        assertEquals(OperationKind.ORDER_STATUS, Operation.CHECKOUT_ORDER_STATUS.kind());
        assertTrue(Operation.CHECKOUT_CANCEL_ORDER.isWrite());
        assertFalse(Operation.CHECKOUT_LIST_ORDERS.isWrite());
        assertTrue(Operation.UTILITYPAYMENT_PROCESS.isWrite());
    }

    @Test
    void moneyParsingIsStrict() {
        assertEquals(10000, Money.parseTzs("10,000").units());
        assertEquals(10000, Money.parseTzs(" 10000 ").units());
        assertEquals(10000, Money.parseTzs("10000.00").units());
        for (String bad : new String[] {"10000.5", "1e3", "-5", "", "10,00", "1,0000", "abc", "99999999999999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> Money.parseTzs(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> Money.parseTzs(null));
    }

    @Test
    void payoutsRejectNonTzsAndNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new Requests.UtilityPayment("T", "LUKU", "1", Money.usd(5), null));
        assertThrows(IllegalArgumentException.class, () -> new Requests.WalletCashIn("T", "CASHIN", "1", Money.tzs(0), null));
        assertThrows(IllegalArgumentException.class, () -> new Requests.WalletCashIn("T", "CASHIN", "1", Money.tzs(-1), null));
    }

    @Test
    void extremeExponentsAreBoundedAndRejected() {
        io.github.selcomdevelopers.selcom.signing.Signer signer = new Hs256Signer("test-secret-0001");
        for (String number : new String[] {"1e10000000", "1e100000000", "1e1000000000", "-1e10000000"}) {
            SigningException e = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    assertThrows(SigningException.class, () -> sign(new java.math.BigDecimal(number), signer)), number);
            assertEquals(SigningException.Code.NUMBER_OUT_OF_RANGE, e.code(), number);
        }
        for (String number : new String[] {"1e-999999999", "-1e-999999999", "0.5", "8000.5"}) {
            SigningException e = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    assertThrows(SigningException.class, () -> sign(new java.math.BigDecimal(number), signer)), number);
            assertEquals(SigningException.Code.NON_INTEGER_NUMBER, e.code(), number);
        }
        assertEquals(1000L, sign(new java.math.BigDecimal("1E+3"), signer));
        assertEquals(8000L, sign(new java.math.BigDecimal("8000.00"), signer));
        assertEquals(0L, sign(new java.math.BigDecimal("0.000"), signer));
        assertEquals(-5L, sign(new java.math.BigDecimal("-5.0"), signer));
        assertEquals(9007199254740991L, sign(new java.math.BigDecimal("9007199254740991"), signer));
        assertEquals(SigningException.Code.NUMBER_OUT_OF_RANGE, assertThrows(SigningException.class,
                () -> sign(new java.math.BigDecimal("9007199254740992"), signer)).code());
        assertEquals(SigningException.Code.NUMBER_OUT_OF_RANGE, assertThrows(SigningException.class,
                () -> sign(new java.math.BigDecimal("1E+16"), signer)).code());
        assertEquals(SigningException.Code.NON_INTEGER_NUMBER, assertThrows(SigningException.class,
                () -> sign(Double.NaN, signer)).code());
    }

    private static Object sign(Object value, io.github.selcomdevelopers.selcom.signing.Signer signer) {
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("amount", value);
        return io.github.selcomdevelopers.selcom.signing.RequestSigner.sign(io.github.selcomdevelopers.selcom.signing.HttpMethod.POST,
                params, "2026-11-21T10:30:46+03:00", "test-key-0001", signer).flattened().get(0).value();
    }

    @Test
    void timestampsAreGeneratedInTheColonFormInDarEsSalaam() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T07:30:46.987Z"), ZoneOffset.UTC);
        assertEquals("2026-11-21T10:30:46+03:00", Timestamps.now(clock, Timestamps.DEFAULT_ZONE));
        assertEquals("2026-11-21T07:30:46+00:00", Timestamps.now(clock, ZoneId.of("UTC")));
        assertEquals(java.time.OffsetDateTime.parse("2026-11-21T10:30:46+03:00"), Timestamps.parse("2026-11-21T10:30:46+0300"));
        for (String bad : new String[] {"2026-11-21T07:30:46Z", "2026-11-21 10:30:46+03:00", "2026-13-21T10:30:46+03:00", "2026-11-21T10:30:46.5+03:00", ""}) {
            assertThrows(SigningException.class, () -> Timestamps.parse(bad), bad);
        }
    }
}
