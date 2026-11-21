package io.github.selcomdevelopers.selcom.transport;

/**
 * Sends one HTTP request. Replace it to use your own HTTP client, a proxy, logging, or a test fake.
 *
 * <p>An implementation must apply the request's timeouts, must not follow redirects and must not retry. When no
 * response is received it throws a {@link TransportException} that says whether the request provably never left
 * ({@link TransportException.Outcome#NOT_SENT}) or may have reached the gateway
 * ({@link TransportException.Outcome#UNKNOWN_OUTCOME}). If it cannot tell, the answer is UNKNOWN_OUTCOME.
 */
@FunctionalInterface
public interface Transport {

    /**
     * Sends the request and returns whatever response arrives, whatever its HTTP status.
     *
     * @param request the request
     * @return the response
     * @throws TransportException if no HTTP response was received
     */
    TransportResponse send(TransportRequest request);
}
