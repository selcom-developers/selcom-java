package io.github.selcomdevelopers.selcom;

import io.github.selcomdevelopers.selcom.signing.Timestamps;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Client configuration. The environment is chosen explicitly and has no default, so a missing value is an error at
 * construction, never a silent call to production. The secret and the PIN never appear in {@link #toString()}.
 */
public final class SelcomConfig {
    private final Environment environment;
    private final URI baseUrl;
    private final Credentials credentials;
    private final Credentials checkoutCredentials;
    private final String vendor;
    private final String pin;
    private final Timeouts queryTimeouts;
    private final Timeouts paymentTimeouts;
    private final ZoneId zone;

    private SelcomConfig(Builder b) {
        if (b.environment != null && b.customBaseUrl != null) {
            throw new ConfigurationException("choose either an environment or a custom base URL, not both");
        }
        if (b.environment == null && b.customBaseUrl == null) {
            throw new ConfigurationException("no environment chosen: set environment(SANDBOX) or environment(PRODUCTION); there is no default");
        }
        this.environment = b.environment;
        this.baseUrl = b.environment != null ? b.environment.baseUrl() : parseBaseUrl(b.customBaseUrl);
        if (b.credentials == null) {
            throw new ConfigurationException("API key and secret are required");
        }
        this.credentials = b.credentials;
        this.checkoutCredentials = b.checkoutCredentials;
        if (b.vendor == null || b.vendor.isBlank()) {
            throw new ConfigurationException("vendor is required");
        }
        this.vendor = b.vendor;
        this.pin = b.pin == null || b.pin.isEmpty() ? null : b.pin;
        this.queryTimeouts = b.queryTimeouts;
        this.paymentTimeouts = b.paymentTimeouts;
        this.zone = b.zone;
    }

    private static URI parseBaseUrl(String text) {
        try {
            URI uri = new URI(text.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
                throw new ConfigurationException("custom base URL must be an absolute URL with no credentials, query or fragment");
            }
            boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
            if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
                throw new ConfigurationException("custom base URL must be https (http is allowed only for localhost)");
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            return new URI(scheme, null, host, uri.getPort(), path, null, null);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("custom base URL is not a valid URL");
        }
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The chosen environment, or null when a custom base URL is used.
     *
     * @return the environment, or null
     */
    public Environment environment() {
        return environment;
    }

    /**
     * The base URL, without a trailing slash.
     *
     * @return the base URL
     */
    public URI baseUrl() {
        return baseUrl;
    }

    /**
     * The credentials for payouts, and for checkout unless separate checkout credentials are set.
     *
     * @return the credentials
     */
    public Credentials credentials() {
        return credentials;
    }

    /**
     * The credentials checkout operations are signed with.
     *
     * @return the checkout credentials if set, otherwise {@link #credentials()}
     */
    public Credentials checkoutCredentials() {
        return checkoutCredentials != null ? checkoutCredentials : credentials;
    }

    /**
     * The vendor (till or float account) ID.
     *
     * @return the vendor
     */
    public String vendor() {
        return vendor;
    }

    /**
     * The float PIN, which payout operations need.
     *
     * @return the PIN, or null when none is configured
     */
    public String pin() {
        return pin;
    }

    /**
     * The timeouts for queries and everything that moves no money.
     *
     * @return the profile
     */
    public Timeouts queryTimeouts() {
        return queryTimeouts;
    }

    /**
     * The timeouts for money-moving operations.
     *
     * @return the profile
     */
    public Timeouts paymentTimeouts() {
        return paymentTimeouts;
    }

    /**
     * The zone whose offset is printed in timestamps.
     *
     * @return the zone
     */
    public ZoneId zone() {
        return zone;
    }

    /**
     * The timeouts that apply to an operation kind.
     *
     * @param kind the kind
     * @return the payment profile for payments, the query profile for everything else
     */
    public Timeouts timeoutsFor(OperationKind kind) {
        return kind == OperationKind.PAYMENT ? paymentTimeouts : queryTimeouts;
    }

    @Override
    public String toString() {
        return "SelcomConfig[" + (environment != null ? environment : "custom " + baseUrl) + ", vendor=" + vendor
                + ", credentials=" + credentials + ", checkoutCredentials=" + (checkoutCredentials == null ? "same" : checkoutCredentials)
                + ", pin=" + (pin == null ? "none" : "[REDACTED]") + "]";
    }

    /** Builds a {@link SelcomConfig}. */
    public static final class Builder {
        private Environment environment;
        private String customBaseUrl;
        private Credentials credentials;
        private Credentials checkoutCredentials;
        private String vendor;
        private String pin;
        private Timeouts queryTimeouts = Timeouts.QUERY;
        private Timeouts paymentTimeouts = Timeouts.PAYMENT;
        private ZoneId zone = Timestamps.DEFAULT_ZONE;

        private Builder() {
        }

        /**
         * Chooses the environment. Required, unless {@link #customBaseUrl} is used.
         *
         * @param value sandbox or production
         * @return this builder
         */
        public Builder environment(Environment value) {
            this.environment = Objects.requireNonNull(value, "environment");
            return this;
        }

        /**
         * Points the client at a custom base URL (a simulator or a proxy) instead of an environment. This is its own
         * explicit choice and cannot be combined with {@link #environment}. It must be https, or http on localhost.
         *
         * @param url the base URL
         * @return this builder
         */
        public Builder customBaseUrl(String url) {
            this.customBaseUrl = Objects.requireNonNull(url, "url");
            return this;
        }

        /**
         * Sets the API key and secret.
         *
         * @param apiKey the API key
         * @param secret the API secret
         * @return this builder
         */
        public Builder credentials(String apiKey, String secret) {
            this.credentials = new Credentials(apiKey, secret);
            return this;
        }

        /**
         * Sets separate credentials for checkout operations, for merchants who use a different key pair there.
         *
         * @param apiKey the API key
         * @param secret the API secret
         * @return this builder
         */
        public Builder checkoutCredentials(String apiKey, String secret) {
            this.checkoutCredentials = new Credentials(apiKey, secret);
            return this;
        }

        /**
         * Sets the vendor (till or float account) ID.
         *
         * @param value the vendor
         * @return this builder
         */
        public Builder vendor(String value) {
            this.vendor = value;
            return this;
        }

        /**
         * Sets the float PIN. Needed only for payout operations.
         *
         * @param value the PIN
         * @return this builder
         */
        public Builder pin(String value) {
            this.pin = value;
            return this;
        }

        /**
         * Overrides the timeouts for queries and everything that moves no money.
         *
         * @param value the profile
         * @return this builder
         */
        public Builder queryTimeouts(Timeouts value) {
            this.queryTimeouts = Objects.requireNonNull(value, "queryTimeouts");
            return this;
        }

        /**
         * Overrides the timeouts for money-moving operations. Keep the read timeout long: timing out early does not stop a
         * payment, it only turns a known answer into an unknown one.
         *
         * @param value the profile
         * @return this builder
         */
        public Builder paymentTimeouts(Timeouts value) {
            this.paymentTimeouts = Objects.requireNonNull(value, "paymentTimeouts");
            return this;
        }

        /**
         * Sets the zone whose offset is printed in timestamps. The default is {@code Africa/Dar_es_Salaam}.
         *
         * @param value the zone
         * @return this builder
         */
        public Builder zone(ZoneId value) {
            this.zone = Objects.requireNonNull(value, "zone");
            return this;
        }

        /**
         * Validates and builds the configuration.
         *
         * @return the configuration
         * @throws ConfigurationException if the environment, credentials or vendor are missing or invalid
         */
        public SelcomConfig build() {
            return new SelcomConfig(this);
        }
    }
}
