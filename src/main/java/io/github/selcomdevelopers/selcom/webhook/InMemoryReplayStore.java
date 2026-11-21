package io.github.selcomdevelopers.selcom.webhook;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A thread-safe, in-process {@link ReplayStore}. Entries expire after the window. It does not span processes. */
public final class InMemoryReplayStore implements ReplayStore {
    private final Map<String, Instant> expiries = new HashMap<>();

    @Override
    public synchronized boolean markIfNew(String digest, Instant now, Instant expiresAt) {
        expiries.values().removeIf(expiry -> !expiry.isAfter(now));
        if (expiries.containsKey(digest)) {
            return false;
        }
        expiries.put(digest, expiresAt);
        return true;
    }
}
