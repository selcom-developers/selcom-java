package io.github.selcomdevelopers.selcom.transport;

import io.github.selcomdevelopers.selcom.ConfigurationException;
import io.github.selcomdevelopers.selcom.signing.HttpMethod;
import io.github.selcomdevelopers.selcom.transport.TransportException.Outcome;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The default {@link Transport}, on {@code java.net.http.HttpClient}. It follows no redirects, adds no retry of its own
 * and applies the request's connect and read timeouts, including to reading the body.
 *
 * <p>Two JDK behaviours matter. By default the JDK resends a GET once if a pooled connection was dropped, which is
 * harmless for reads. The property {@code -Djdk.httpclient.enableAllMethodRetry=true} makes it resend any method, which
 * conflicts with sending a payment or write exactly once; this transport therefore refuses to send a POST or DELETE
 * while that property is set, and throws a {@link ConfigurationException} before anything is sent.
 *
 * <p>Use {@link #JdkHttpTransport()} for a transport that manages its own clients (one per connect timeout), or
 * {@link #JdkHttpTransport(HttpClient)} to bring your own client, for example one with a proxy, a custom
 * {@code SSLContext} or an executor. A client you bring is used as it is: its own connect timeout applies, and it should
 * not follow redirects.
 */
public final class JdkHttpTransport implements Transport {
    /** The JDK system property that makes the HTTP client resend any method after a dropped connection. */
    static final String RETRY_PROPERTY = "jdk.httpclient.enableAllMethodRetry";

    private final HttpClient fixedClient;
    private final Map<Duration, HttpClient> clients = new ConcurrentHashMap<>();

    /** Creates a transport that builds its own HTTP/1.1 clients, one per connect timeout. */
    public JdkHttpTransport() {
        this.fixedClient = null;
    }

    /**
     * Creates a transport around your own client.
     *
     * @param client the client to use for every request
     */
    public JdkHttpTransport(HttpClient client) {
        this.fixedClient = Objects.requireNonNull(client, "client");
    }

    @Override
    public TransportResponse send(TransportRequest request) {
        refuseSilentWriteRetry(request);
        HttpClient client = fixedClient != null ? fixedClient : clients.computeIfAbsent(request.timeouts().connect(), this::newClient);
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeouts().read());
        request.headers().forEach(builder::header);
        HttpRequest.BodyPublisher publisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
        HttpRequest httpRequest = builder.method(request.method().name(), publisher).build();

        // The request timeout covers the wait for the response headers. The overall deadline below also covers reading the
        // body, so no operation can hang.
        Duration deadline = request.timeouts().connect().plus(request.timeouts().read());
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            return new TransportResponse(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TransportException(Outcome.UNKNOWN_OUTCOME, "no complete response within the read timeout", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new TransportException(JdkErrors.classify(cause), describe(cause), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TransportException(Outcome.UNKNOWN_OUTCOME, "interrupted while waiting for the response", e);
        }
    }

    private static void refuseSilentWriteRetry(TransportRequest request) {
        String property = System.getProperty(RETRY_PROPERTY);
        if (request.method() != HttpMethod.GET && property != null && !property.equalsIgnoreCase("false")) {
            throw new ConfigurationException("refusing to send a " + request.method()
                    + ": -D" + RETRY_PROPERTY + " makes the JDK resend writes, but writes are sent exactly once. Unset it.");
        }
    }

    private HttpClient newClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
    }

    private static String describe(Throwable cause) {
        return cause.getClass().getSimpleName();
    }
}
