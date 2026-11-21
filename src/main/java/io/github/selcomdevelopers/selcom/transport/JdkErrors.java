package io.github.selcomdevelopers.selcom.transport;

import io.github.selcomdevelopers.selcom.transport.TransportException.Outcome;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import javax.net.ssl.SSLHandshakeException;

/**
 * Decides, from the exception the JDK HTTP client threw, whether the request provably never left.
 *
 * <p>Only failures that happen before any request byte can have been written count as
 * {@link Outcome#NOT_SENT}: DNS failure, connection refused or timed out, no route, and a failed TLS handshake. Everything
 * else, including every timeout after the connection was made, is {@link Outcome#UNKNOWN_OUTCOME}.
 */
public final class JdkErrors {
    private static final int MAX_DEPTH = 10;

    private JdkErrors() {
    }

    /**
     * Classifies a failure by walking its cause chain from the outside in; the first recognised cause decides.
     *
     * @param failure what the HTTP client threw
     * @return NOT_SENT only when the request provably never reached the gateway; otherwise UNKNOWN_OUTCOME
     */
    public static Outcome classify(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof HttpConnectTimeoutException) {
                return Outcome.NOT_SENT;
            }
            if (current instanceof HttpTimeoutException) {
                return Outcome.UNKNOWN_OUTCOME;
            }
            if (current instanceof ConnectException connect) {
                // The JDK also reports "HTTP/1.1 header parser received no bytes" as a ConnectException, when a pooled
                // connection is closed after the request was written. That is not a failure to connect.
                String message = connect.getMessage();
                if (message != null && message.contains("received no bytes")) {
                    return Outcome.UNKNOWN_OUTCOME;
                }
                return Outcome.NOT_SENT;
            }
            if (current instanceof UnknownHostException || current instanceof UnresolvedAddressException
                    || current instanceof NoRouteToHostException || current instanceof SSLHandshakeException) {
                return Outcome.NOT_SENT;
            }
            current = current.getCause();
        }
        return Outcome.UNKNOWN_OUTCOME;
    }
}
