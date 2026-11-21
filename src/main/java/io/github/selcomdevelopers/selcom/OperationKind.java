package io.github.selcomdevelopers.selcom;

/** How an operation's response is classified, which timeouts it uses and whether it may ever be retried. */
public enum OperationKind {
    /** Moves money, or asks the buyer to approve a payment. Never retried. */
    PAYMENT,
    /** Asks for the verdict on an earlier payout by {@code transid}. */
    STATUS_QUERY,
    /** Asks for the state of a checkout order; the verdict is {@code data[0].payment_status}. */
    ORDER_STATUS,
    /** Creates, cancels, lists, looks up or reads. Moves no money. */
    OTHER
}
