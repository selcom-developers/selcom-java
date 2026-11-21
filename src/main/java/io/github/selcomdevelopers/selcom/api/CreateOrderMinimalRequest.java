package io.github.selcomdevelopers.selcom.api;

import io.github.selcomdevelopers.selcom.Money;
import io.github.selcomdevelopers.selcom.Operation;
import io.github.selcomdevelopers.selcom.redact.Redactor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code checkout.create-order-minimal}: an order for non-card payment (mobile-wallet push, or a token or QR code the
 * buyer pays with). It is the first step of a USSD push; the second is {@link Requests.WalletPayment}.
 *
 * <p>URLs are given as plain URLs and base64-encoded for you, before signing.
 */
public final class CreateOrderMinimalRequest implements ApiRequest {
    private final String orderId;
    private final String buyerEmail;
    private final String buyerName;
    private final String buyerPhone;
    private final Money amount;
    private final String redirectUrl;
    private final String cancelUrl;
    private final String webhookUrl;
    private final String buyerRemarks;
    private final String merchantRemarks;
    private final int noOfItems;
    private final String headerColour;
    private final String linkColour;
    private final String buttonColour;
    private final Integer expiryMinutes;

    private CreateOrderMinimalRequest(Builder b) {
        this.orderId = Requests.require(b.orderId, "orderId");
        this.buyerEmail = Requests.require(b.buyerEmail, "buyerEmail");
        this.buyerName = Requests.require(b.buyerName, "buyerName");
        this.buyerPhone = Requests.require(b.buyerPhone, "buyerPhone");
        this.amount = Requests.requirePositive(b.amount);
        this.redirectUrl = b.redirectUrl;
        this.cancelUrl = b.cancelUrl;
        this.webhookUrl = b.webhookUrl;
        this.buyerRemarks = b.buyerRemarks;
        this.merchantRemarks = b.merchantRemarks;
        if (b.noOfItems < 1) {
            throw new IllegalArgumentException("noOfItems must be at least 1");
        }
        this.noOfItems = b.noOfItems;
        this.headerColour = b.headerColour;
        this.linkColour = b.linkColour;
        this.buttonColour = b.buttonColour;
        if (b.expiryMinutes != null && b.expiryMinutes < 1) {
            throw new IllegalArgumentException("expiryMinutes must be at least 1");
        }
        this.expiryMinutes = b.expiryMinutes;
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Operation operation() {
        return Operation.CHECKOUT_CREATE_ORDER_MINIMAL;
    }

    @Override
    public String orderId() {
        return orderId;
    }

    @Override
    public Map<String, Object> params(Defaults defaults) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("vendor", defaults.vendor());
        p.put("order_id", orderId);
        p.put("buyer_email", buyerEmail);
        p.put("buyer_name", buyerName);
        p.put("buyer_phone", buyerPhone);
        p.put("amount", amount.units());
        p.put("currency", amount.currency().name());
        CreateOrderRequest.putUrl(p, "redirect_url", redirectUrl);
        CreateOrderRequest.putUrl(p, "cancel_url", cancelUrl);
        CreateOrderRequest.putUrl(p, "webhook", webhookUrl);
        CreateOrderRequest.putIfPresent(p, "buyer_remarks", buyerRemarks);
        CreateOrderRequest.putIfPresent(p, "merchant_remarks", merchantRemarks);
        p.put("no_of_items", noOfItems);
        CreateOrderRequest.putIfPresent(p, "header_colour", headerColour);
        CreateOrderRequest.putIfPresent(p, "link_colour", linkColour);
        CreateOrderRequest.putIfPresent(p, "button_colour", buttonColour);
        if (expiryMinutes != null) {
            p.put("expiry", expiryMinutes);
        }
        return p;
    }

    @Override
    public String toString() {
        return "CreateOrderMinimalRequest" + Redactor.defaults().redactMap(params(new Defaults("-", null)));
    }

    /** Builds a {@link CreateOrderMinimalRequest}. Required: orderId, buyerEmail, buyerName, buyerPhone, amount, noOfItems. */
    public static final class Builder {
        private String orderId;
        private String buyerEmail;
        private String buyerName;
        private String buyerPhone;
        private Money amount;
        private String redirectUrl;
        private String cancelUrl;
        private String webhookUrl;
        private String buyerRemarks;
        private String merchantRemarks;
        private int noOfItems;
        private String headerColour;
        private String linkColour;
        private String buttonColour;
        private Integer expiryMinutes;

        private Builder() {
        }

        /**
         * Sets the merchant's unique order ID.
         *
         * @param value the ID
         * @return this builder
         */
        public Builder orderId(String value) {
            this.orderId = value;
            return this;
        }

        /**
         * Sets the buyer's e-mail address.
         *
         * @param value the address
         * @return this builder
         */
        public Builder buyerEmail(String value) {
            this.buyerEmail = value;
            return this;
        }

        /**
         * Sets the buyer's full name.
         *
         * @param value the name
         * @return this builder
         */
        public Builder buyerName(String value) {
            this.buyerName = value;
            return this;
        }

        /**
         * Sets the buyer's phone number in international form, {@code 255...}.
         *
         * @param value the number
         * @return this builder
         */
        public Builder buyerPhone(String value) {
            this.buyerPhone = value;
            return this;
        }

        /**
         * Sets the amount and currency. The currency is always explicit.
         *
         * @param value the money
         * @return this builder
         */
        public Builder amount(Money value) {
            this.amount = value;
            return this;
        }

        /**
         * Sets the URL the buyer returns to after paying. Give the plain URL; it is base64-encoded for you.
         *
         * @param url the URL
         * @return this builder
         */
        public Builder redirectUrl(String url) {
            this.redirectUrl = url;
            return this;
        }

        /**
         * Sets the URL the buyer returns to after cancelling. Give the plain URL; it is base64-encoded for you.
         *
         * @param url the URL
         * @return this builder
         */
        public Builder cancelUrl(String url) {
            this.cancelUrl = url;
            return this;
        }

        /**
         * Sets the URL that receives the payment callback. Give the plain URL; it is base64-encoded for you.
         *
         * @param url the URL
         * @return this builder
         */
        public Builder webhookUrl(String url) {
            this.webhookUrl = url;
            return this;
        }

        /**
         * Sets free-text remarks from the buyer.
         *
         * @param value the text
         * @return this builder
         */
        public Builder buyerRemarks(String value) {
            this.buyerRemarks = value;
            return this;
        }

        /**
         * Sets free-text remarks from the merchant.
         *
         * @param value the text
         * @return this builder
         */
        public Builder merchantRemarks(String value) {
            this.merchantRemarks = value;
            return this;
        }

        /**
         * Sets the number of items in the order.
         *
         * @param value the count, at least 1
         * @return this builder
         */
        public Builder noOfItems(int value) {
            this.noOfItems = value;
            return this;
        }

        /**
         * Sets the hosted page's colours, for example {@code #FF0012}.
         *
         * @param header the header colour, or null
         * @param link the link colour, or null
         * @param button the button colour, or null
         * @return this builder
         */
        public Builder colours(String header, String link, String button) {
            this.headerColour = header;
            this.linkColour = link;
            this.buttonColour = button;
            return this;
        }

        /**
         * Sets the minutes until the order expires.
         *
         * @param minutes the minutes, at least 1
         * @return this builder
         */
        public Builder expiryMinutes(int minutes) {
            this.expiryMinutes = minutes;
            return this;
        }

        /**
         * Validates and builds the request.
         *
         * @return the request
         * @throws IllegalArgumentException if a required field is missing or invalid
         */
        public CreateOrderMinimalRequest build() {
            return new CreateOrderMinimalRequest(this);
        }
    }
}
