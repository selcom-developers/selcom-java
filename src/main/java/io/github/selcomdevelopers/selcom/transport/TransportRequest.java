package io.github.selcomdevelopers.selcom.transport;

import io.github.selcomdevelopers.selcom.Timeouts;
import io.github.selcomdevelopers.selcom.redact.Redactor;
import io.github.selcomdevelopers.selcom.signing.HttpMethod;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One request for a {@link Transport} to send.
 *
 * @param method the HTTP method
 * @param uri the full URI, including the query string for GET and DELETE
 * @param headers the headers to send
 * @param body the UTF-8 body for POST, or null
 * @param timeouts the connect and read timeouts to apply
 */
public record TransportRequest(HttpMethod method, URI uri, Map<String, String> headers, String body, Timeouts timeouts) {

    /** Validates and copies the headers. */
    public TransportRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(timeouts, "timeouts");
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /** Redacts the headers, the query string and the body, so a request can be logged safely. */
    @Override
    public String toString() {
        Redactor redactor = uri.getRawPath().startsWith("/v1/walletcashin/")
                ? Redactor.treatingUtilityRefAsPhone() : Redactor.defaults();
        String query = uri.getRawQuery();
        String target = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath()
                + (query == null ? "" : "?" + redactor.redactQuery(query));
        return "TransportRequest[" + method + " " + target + ", headers=" + redactor.redactHeaders(headers)
                + (body == null ? "" : ", body=" + redactor.redactJson(body)) + "]";
    }
}
