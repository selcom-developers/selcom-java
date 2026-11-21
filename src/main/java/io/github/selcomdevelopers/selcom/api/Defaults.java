package io.github.selcomdevelopers.selcom.api;

import io.github.selcomdevelopers.selcom.ConfigurationException;
import java.util.Objects;

/**
 * The configured vendor and float PIN that requests fill in for you. The PIN never appears in {@link #toString()}.
 *
 * @param vendor the vendor (till or float account) ID
 * @param pin the float PIN, or null when none is configured
 */
public record Defaults(String vendor, String pin) {

    /** Validates the vendor. */
    public Defaults {
        Objects.requireNonNull(vendor, "vendor");
    }

    /**
     * The float PIN, which payout operations need.
     *
     * @return the PIN
     * @throws ConfigurationException if no PIN is configured
     */
    public String requirePin() {
        if (pin == null || pin.isEmpty()) {
            throw new ConfigurationException("this operation needs the float PIN, and none is configured");
        }
        return pin;
    }

    @Override
    public String toString() {
        return "Defaults[vendor=" + vendor + ", pin=" + (pin == null ? "none" : "[REDACTED]") + "]";
    }
}
