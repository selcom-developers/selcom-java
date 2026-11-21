package io.github.selcomdevelopers.selcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.selcomdevelopers.selcom.signing.HttpMethod;
import io.github.selcomdevelopers.selcom.transport.JdkErrors;
import io.github.selcomdevelopers.selcom.transport.JdkHttpTransport;
import io.github.selcomdevelopers.selcom.transport.TransportException;
import io.github.selcomdevelopers.selcom.transport.TransportException.Outcome;
import io.github.selcomdevelopers.selcom.transport.TransportRequest;
import io.github.selcomdevelopers.selcom.transport.TransportResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The default transport against a throwaway server on loopback. No external network. */
class JdkTransportTest {
    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile String lastMethod;
    private volatile String lastBody;
    private volatile String lastContentType;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/echo", exchange -> {
            hits.incrementAndGet();
            lastMethod = exchange.getRequestMethod();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] out = "{\"resultcode\":\"000\",\"name\":\"Zawadi é\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(418, out.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(out);
            }
        });
        server.createContext("/slow", exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/echo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static Timeouts timeouts(long connectMs, long readMs) {
        return new Timeouts(Duration.ofMillis(connectMs), Duration.ofMillis(readMs));
    }

    @Test
    void returnsTheResponseWhateverTheStatusAndDecodesUtf8() {
        JdkHttpTransport transport = new JdkHttpTransport();
        TransportResponse response = transport.send(new TransportRequest(HttpMethod.POST, uri("/echo"),
                Map.of("Content-Type", "application/json"), "{\"a\":\"b\"}", timeouts(2000, 2000)));
        assertEquals(418, response.status());
        assertTrue(response.body().contains("Zawadi é"), response.body());
        assertEquals("POST", lastMethod);
        assertEquals("{\"a\":\"b\"}", lastBody);
        assertEquals("application/json", lastContentType);
    }

    @Test
    void getAndDeleteSendNoBody() {
        JdkHttpTransport transport = new JdkHttpTransport();
        transport.send(new TransportRequest(HttpMethod.DELETE, uri("/echo?order_id=ORD-1"), Map.of(), null, timeouts(2000, 2000)));
        assertEquals("DELETE", lastMethod);
        assertEquals("", lastBody);
    }

    @Test
    void aConnectionRefusedIsNotSent() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        TransportException e = assertThrows(TransportException.class, () -> new JdkHttpTransport().send(new TransportRequest(
                HttpMethod.POST, URI.create("http://127.0.0.1:" + port + "/echo"), Map.of(), "{}", timeouts(2000, 2000))));
        assertEquals(Outcome.NOT_SENT, e.outcome());
    }

    @Test
    void aReadTimeoutIsAnUnknownOutcome() {
        TransportException e = assertThrows(TransportException.class, () -> new JdkHttpTransport().send(new TransportRequest(
                HttpMethod.POST, uri("/slow"), Map.of(), "{}", timeouts(2000, 300))));
        assertEquals(Outcome.UNKNOWN_OUTCOME, e.outcome());
        assertEquals(1, hits.get(), "the request reached the server, and is not retried");
    }

    @Test
    void redirectsAreNotFollowed() {
        TransportResponse response = new JdkHttpTransport().send(new TransportRequest(
                HttpMethod.GET, uri("/redirect"), Map.of(), null, timeouts(2000, 2000)));
        assertEquals(302, response.status());
        assertEquals(1, hits.get());
    }

    @Test
    void writesAreRefusedWhileTheJdkAllMethodRetryPropertyIsSet() {
        String property = "jdk.httpclient.enableAllMethodRetry";
        String old = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            JdkHttpTransport transport = new JdkHttpTransport();
            for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.DELETE}) {
                assertThrows(ConfigurationException.class, () -> transport.send(new TransportRequest(method, uri("/echo"),
                        Map.of(), method == HttpMethod.POST ? "{}" : null, timeouts(2000, 2000))), method.name());
            }
            assertEquals(0, hits.get(), "nothing is sent");
            assertEquals(418, transport.send(new TransportRequest(HttpMethod.GET, uri("/echo"), Map.of(), null, timeouts(2000, 2000))).status());
            System.setProperty(property, "false");
            assertEquals(418, transport.send(new TransportRequest(HttpMethod.POST, uri("/echo"), Map.of(), "{}", timeouts(2000, 2000))).status());
        } finally {
            if (old == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, old);
            }
        }
    }

    @Test
    void errorClassification() {
        assertEquals(Outcome.NOT_SENT, JdkErrors.classify(new HttpConnectTimeoutException("connect timed out")));
        assertEquals(Outcome.NOT_SENT, JdkErrors.classify(new ConnectException("Connection refused")));
        assertEquals(Outcome.NOT_SENT, JdkErrors.classify(new IOException("wrapped", new UnknownHostException("nope.example"))));
        assertEquals(Outcome.NOT_SENT, JdkErrors.classify(new ConnectException("x") {
            private static final long serialVersionUID = 1L;

            @Override public synchronized Throwable getCause() {
                return new UnresolvedAddressException();
            }
        }));
        assertEquals(Outcome.NOT_SENT, JdkErrors.classify(new SSLHandshakeException("handshake failed")));
        assertEquals(Outcome.UNKNOWN_OUTCOME, JdkErrors.classify(new HttpTimeoutException("request timed out")));
        assertEquals(Outcome.UNKNOWN_OUTCOME, JdkErrors.classify(new IOException("Connection reset")));
        assertEquals(Outcome.UNKNOWN_OUTCOME, JdkErrors.classify(new SSLException("Unexpected end of stream mid-flight")));
        assertEquals(Outcome.UNKNOWN_OUTCOME, JdkErrors.classify(new ConnectException("HTTP/1.1 header parser received no bytes")));
        assertEquals(Outcome.UNKNOWN_OUTCOME, JdkErrors.classify(new IllegalStateException("who knows")));
    }
}
