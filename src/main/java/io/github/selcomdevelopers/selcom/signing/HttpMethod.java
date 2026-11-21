package io.github.selcomdevelopers.selcom.signing;

/** The HTTP methods the gateway uses. All three are signed. */
public enum HttpMethod {
    /** Parameters travel in the query string. */
    GET,
    /** Parameters travel as a flat JSON body. */
    POST,
    /** Parameters travel in the query string. */
    DELETE;

    /**
     * Whether parameters go in the query string rather than a body.
     *
     * @return true for GET and DELETE
     */
    public boolean usesQuery() {
        return this != POST;
    }
}
