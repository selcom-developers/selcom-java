package io.github.selcomdevelopers.selcom.signing;

import java.util.List;
import java.util.Map;

/**
 * Everything that goes on the wire for one signed request, as produced by {@link RequestSigner}.
 *
 * @param timestamp the timestamp, character for character as sent and signed
 * @param flattened the flattened, normalised fields in send order
 * @param signedFields the {@code Signed-Fields} header value
 * @param signingString the text that was signed
 * @param digest the base64 digest
 * @param authorization the {@code Authorization} header value
 * @param headers the five signing headers: Authorization, Digest-Method, Digest, Timestamp, Signed-Fields
 * @param query the percent-encoded query string for GET and DELETE; null for POST
 * @param body the compact flat JSON body for POST; null for GET and DELETE
 */
public record SignedRequest(
        String timestamp,
        List<RequestSigner.Field> flattened,
        String signedFields,
        String signingString,
        String digest,
        String authorization,
        Map<String, String> headers,
        String query,
        String body) {

    /** Makes the lists and maps unmodifiable. */
    public SignedRequest {
        flattened = List.copyOf(flattened);
        headers = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(headers));
    }

    /** Shows the field names only: the digest and the field values (which may be personal data) are left out. */
    @Override
    public String toString() {
        return "SignedRequest[timestamp=" + timestamp + ", signedFields=" + signedFields + "]";
    }
}
