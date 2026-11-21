package io.github.selcomdevelopers.selcom.signing;

/**
 * The digest step of signing, behind an interface so that another algorithm (RS256) can be added without
 * touching request building.
 */
public interface Signer {

    /**
     * The algorithm name, sent as the {@code Digest-Method} header.
     *
     * @return for example {@code HS256}
     */
    String method();

    /**
     * Signs the signing string.
     *
     * @param signingString the text built from the timestamp and the flattened fields
     * @return the base64 digest; never empty
     */
    String sign(String signingString);
}
