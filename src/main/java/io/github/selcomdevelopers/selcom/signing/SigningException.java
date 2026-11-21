package io.github.selcomdevelopers.selcom.signing;

import io.github.selcomdevelopers.selcom.SelcomException;

/** A request cannot be signed. The client must not send it. */
public class SigningException extends SelcomException {
    private static final long serialVersionUID = 1L;

    /** Why signing was refused. The names match the {@code error} codes in the spec's test vectors. */
    public enum Code {
        /** The API secret is empty. */
        EMPTY_SECRET,
        /** The API key is empty. */
        EMPTY_API_KEY,
        /** A number has a fractional part. */
        NON_INTEGER_NUMBER,
        /** An integer is beyond plus or minus 2^53 - 1. */
        NUMBER_OUT_OF_RANGE,
        /** A value is an array, or some other type with no flat form. */
        UNSUPPORTED_TYPE,
        /** A key is empty or contains a character outside {@code A-Z a-z 0-9 _ -}. */
        INVALID_KEY,
        /** A key appears twice after flattening. */
        DUPLICATE_KEY,
        /** A nested object is empty. */
        EMPTY_OBJECT,
        /** There is nothing to sign. */
        EMPTY_PARAMS,
        /** The timestamp is not {@code yyyy-MM-ddTHH:mm:ss+HH:MM} or {@code +HHMM}. */
        INVALID_TIMESTAMP,
        /** The digest would be empty. */
        EMPTY_DIGEST
    }

    private final Code code;

    /**
     * Creates the exception.
     *
     * @param code why signing was refused
     * @param message detail; never contains a secret
     */
    public SigningException(Code code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    /**
     * Why signing was refused.
     *
     * @return the code
     */
    public Code code() {
        return code;
    }
}
