package io.github.selcomdevelopers.selcom;

import io.github.selcomdevelopers.selcom.transport.Transport;
import io.github.selcomdevelopers.selcom.transport.TransportRequest;
import io.github.selcomdevelopers.selcom.transport.TransportResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A test transport that records requests and answers from a function. No network. */
final class FakeTransport implements Transport {
    final List<TransportRequest> requests = new ArrayList<>();
    private final Function<TransportRequest, TransportResponse> responder;

    FakeTransport(Function<TransportRequest, TransportResponse> responder) {
        this.responder = responder;
    }

    static FakeTransport replying(int status, String body) {
        return new FakeTransport(request -> new TransportResponse(status, body));
    }

    @Override
    public TransportResponse send(TransportRequest request) {
        requests.add(request);
        return responder.apply(request);
    }

    TransportRequest last() {
        return requests.get(requests.size() - 1);
    }
}
