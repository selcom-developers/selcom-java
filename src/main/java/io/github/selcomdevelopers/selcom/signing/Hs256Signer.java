package io.github.selcomdevelopers.selcom.signing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 keyed with the API secret, used as UTF-8 text (it looks like hex; it is not hex-decoded). */
public final class Hs256Signer implements Signer {
    private final byte[] key;

    /**
     * Creates the signer.
     *
     * @param secret the API secret
     * @throws SigningException with {@link SigningException.Code#EMPTY_SECRET} if the secret is empty
     */
    public Hs256Signer(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.isEmpty()) {
            throw new SigningException(SigningException.Code.EMPTY_SECRET, "refusing to sign with an empty secret");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String method() {
        return "HS256";
    }

    @Override
    public String sign(String signingString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    @Override
    public String toString() {
        return "Hs256Signer[secret=[REDACTED]]";
    }
}
