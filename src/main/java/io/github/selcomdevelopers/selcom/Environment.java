package io.github.selcomdevelopers.selcom;

import java.net.URI;

/** A Selcom gateway environment. There is deliberately no default. */
public enum Environment {
    /** The live gateway. Moves real money. */
    PRODUCTION("https://apigw.selcommobile.com"),
    /** The test gateway. */
    SANDBOX("https://apigwtest.selcommobile.com");

    private final URI baseUrl;

    Environment(String baseUrl) {
        this.baseUrl = URI.create(baseUrl);
    }

    /**
     * The base URL of this environment, without a trailing slash.
     *
     * @return the base URL
     */
    public URI baseUrl() {
        return baseUrl;
    }
}
