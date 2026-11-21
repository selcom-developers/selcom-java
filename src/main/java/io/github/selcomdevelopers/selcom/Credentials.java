package io.github.selcomdevelopers.selcom;

import java.util.Objects;

/**
 * An API key and its secret. The secret never appears in {@link #toString()}.
 *
 * @param apiKey the API key
 * @param secret the API secret
 */
public record Credentials(String apiKey, String secret) {

    /** Validates that neither part is blank. */
    public Credentials {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(secret, "secret");
        if (apiKey.isBlank()) {
            throw new ConfigurationException("API key is empty");
        }
        if (secret.isBlank()) {
            throw new ConfigurationException("API secret is empty");
        }
    }

    private String maskedKey() {
        return apiKey.length() <= 4 ? "****" : "****" + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String toString() {
        return "Credentials[apiKey=" + maskedKey() + ", secret=[REDACTED]]";
    }
}
