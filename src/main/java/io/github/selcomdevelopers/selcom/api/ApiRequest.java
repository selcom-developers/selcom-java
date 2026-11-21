package io.github.selcomdevelopers.selcom.api;

import io.github.selcomdevelopers.selcom.Operation;
import java.util.Map;

/**
 * A typed request: which operation it is for and the parameters to sign and send, in send order. Implement it to send an
 * operation this library does not model yet; {@code SelcomClient.execute} accepts any instance.
 */
public interface ApiRequest {

    /**
     * The operation this request is for.
     *
     * @return the operation
     */
    Operation operation();

    /**
     * The parameters to sign and send, in send order. Nested maps are flattened to dotted keys by the signer.
     *
     * @param defaults the vendor and float PIN from the configuration
     * @return the parameters
     */
    Map<String, Object> params(Defaults defaults);

    /**
     * The {@code transid} this request is about, if any. Copied into the result when the response does not carry it.
     *
     * @return the id, or null
     */
    default String transId() {
        return null;
    }

    /**
     * The {@code order_id} this request is about, if any. Copied into the result when the response does not carry it.
     *
     * @return the id, or null
     */
    default String orderId() {
        return null;
    }
}
