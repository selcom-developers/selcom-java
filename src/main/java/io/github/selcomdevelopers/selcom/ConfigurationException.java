package io.github.selcomdevelopers.selcom;

/** The client is not configured correctly. Thrown at construction time, or when a payout is attempted without a PIN. */
public class ConfigurationException extends SelcomException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong with the configuration; never contains a secret
     */
    public ConfigurationException(String message) {
        super(message);
    }
}
