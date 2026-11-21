package io.github.selcomdevelopers.selcom;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A whole number of units of a currency. Never a floating-point number; a fraction cannot be expressed.
 *
 * @param units the amount in whole units
 * @param currency the currency, always explicit
 */
public record Money(long units, Currency currency) {
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,3}(,\\d{3})+(\\.0+)?$|^\\d+(\\.0+)?$");

    /** Validates the currency. */
    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    /**
     * Whole Tanzanian shillings.
     *
     * @param units the amount
     * @return the money
     */
    public static Money tzs(long units) {
        return new Money(units, Currency.TZS);
    }

    /**
     * Whole US dollars.
     *
     * @param units the amount
     * @return the money
     */
    public static Money usd(long units) {
        return new Money(units, Currency.USD);
    }

    /**
     * Strictly parses an amount as it arrives in responses and webhooks: {@code "10000"}, {@code "10,000"}
     * or {@code "10000.00"}. Anything else, including a real fraction, is rejected.
     *
     * @param text the text to parse
     * @param currency the currency the amount is in
     * @return the money in whole units
     * @throws IllegalArgumentException if the text is not a whole amount, or does not fit in a {@code long}
     */
    public static Money parse(String text, Currency currency) {
        String trimmed = text == null ? "" : text.strip();
        if (!AMOUNT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("not a whole-unit amount");
        }
        String digits = trimmed.split("\\.", 2)[0].replace(",", "");
        try {
            return new Money(Long.parseLong(digits), currency);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount out of range", e);
        }
    }

    /**
     * Parses a whole-shilling amount.
     *
     * @param text the text to parse
     * @return the amount in TZS
     * @throws IllegalArgumentException if the text is not a whole amount
     * @see #parse(String, Currency)
     */
    public static Money parseTzs(String text) {
        return parse(text, Currency.TZS);
    }

    @Override
    public String toString() {
        return units + " " + currency;
    }
}
