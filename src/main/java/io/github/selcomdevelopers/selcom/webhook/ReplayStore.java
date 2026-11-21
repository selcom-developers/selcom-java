package io.github.selcomdevelopers.selcom.webhook;

import java.time.Instant;

/**
 * Remembers the digests of webhooks already seen, for the length of the verification window. A cache with a time to live is
 * enough; the deduplication on {@code (order_id, transid)} in your own store is the durable guard. Use a shared
 * implementation (for example one backed by a cache server) when more than one process receives webhooks.
 */
public interface ReplayStore {

    /**
     * Records a digest if it has not been seen before its expiry.
     *
     * @param digest the digest of a verified delivery
     * @param now the current time
     * @param expiresAt when the digest may be forgotten: the later of now and the signed timestamp, plus the window
     * @return true if the digest was new and is now recorded; false if it was already there
     */
    boolean markIfNew(String digest, Instant now, Instant expiresAt);
}
