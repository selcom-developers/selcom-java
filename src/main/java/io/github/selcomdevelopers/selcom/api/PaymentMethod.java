package io.github.selcomdevelopers.selcom.api;

/** A payment method offered on an order. Sent as comma-separated text. */
public enum PaymentMethod {
    /** Every method enabled for the vendor. */
    ALL,
    /** Masterpass. */
    MASTERPASS,
    /** Card. */
    CARD,
    /** Mobile-money push. */
    MOBILEMONEYPULL
}
