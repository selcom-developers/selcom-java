package io.github.selcomdevelopers.selcom.api;

import io.github.selcomdevelopers.selcom.Money;
import io.github.selcomdevelopers.selcom.Operation;
import io.github.selcomdevelopers.selcom.redact.Redactor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code checkout.create-order}: an order payable by any enabled method, including cards. Card payments are refused
 * without billing details, so the billing address is required here.
 *
 * <p>The redirect, cancel and webhook URLs are given as plain URLs and base64-encoded for you, before signing: the
 * signature covers the base64 text that is sent.
 */
public final class CreateOrderRequest implements ApiRequest {
    private final String orderId;
    private final String buyerEmail;
    private final String buyerName;
    private final String buyerUserId;
    private final String buyerPhone;
    private final String gatewayBuyerUuid;
    private final Money amount;
    private final Set<PaymentMethod> paymentMethods;
    private final String redirectUrl;
    private final String cancelUrl;
    private final String webhookUrl;
    private final Address billing;
    private final Address shipping;
    private final String buyerRemarks;
    private final String merchantRemarks;
    private final int noOfItems;
    private final String headerColour;
    private final String linkColour;
    private final String buttonColour;
    private final Integer expiryMinutes;

    private CreateOrderRequest(Builder b) {
        this.orderId = Requests.require(b.orderId, "orderId");
        this.buyerEmail = Requests.require(b.buyerEmail, "buyerEmail");
        this.buyerName = Requests.require(b.buyerName, "buyerName");
        this.buyerUserId = b.buyerUserId;
        this.buyerPhone = Requests.require(b.buyerPhone, "buyerPhone");
        this.gatewayBuyerUuid = b.gatewayBuyerUuid;
        this.amount = Requests.requirePositive(b.amount);
        if (b.paymentMethods.isEmpty()) {
            throw new IllegalArgumentException("paymentMethods is required");
        }
        this.paymentMethods = EnumSet.copyOf(b.paymentMethods);
        this.redirectUrl = b.redirectUrl;
        this.cancelUrl = b.cancelUrl;
        this.webhookUrl = b.webhookUrl;
        if (b.billing == null) {
            throw new IllegalArgumentException("billing is required");
        }
        this.billing = b.billing;
        this.shipping = b.shipping;
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
        return Operation.CHECKOUT_CREATE_ORDER;
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
        if (buyerUserId != null) {
            p.put("buyer_userid", buyerUserId);
        }
        p.put("buyer_phone", buyerPhone);
        if (gatewayBuyerUuid != null) {
            p.put("gateway_buyer_uuid", gatewayBuyerUuid);
        }
        p.put("amount", amount.units());
        p.put("currency", amount.currency().name());
        p.put("payment_methods", paymentMethods.stream().map(Enum::name).collect(Collectors.joining(",")));
        putUrl(p, "redirect_url", redirectUrl);
        putUrl(p, "cancel_url", cancelUrl);
        putUrl(p, "webhook", webhookUrl);
        p.put("billing", billing.toParams());
        if (shipping != null) {
            p.put("shipping", shipping.toParams());
        }
        putIfPresent(p, "buyer_remarks", buyerRemarks);
        putIfPresent(p, "merchant_remarks", merchantRemarks);
        p.put("no_of_items", noOfItems);
        putIfPresent(p, "header_colour", headerColour);
        putIfPresent(p, "link_colour", linkColour);
        putIfPresent(p, "button_colour", buttonColour);
        if (expiryMinutes != null) {
            p.put("expiry", expiryMinutes);
        }
        return p;
    }

    static void putIfPresent(Map<String, Object> p, String key, String value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    static void putUrl(Map<String, Object> p, String key, String url) {
        if (url != null) {
            p.put(key, Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public String toString() {
        return "CreateOrderRequest" + Redactor.defaults().redactMap(params(new Defaults("-", null)));
    }

    /** Builds a {@link CreateOrderRequest}. Required: orderId, buyerEmail, buyerName, buyerPhone, amount, payment methods, billing, noOfItems. */
    public static final class Builder {
        private String orderId;
        private String buyerEmail;
        private String buyerName;
        private String buyerUserId;
        private String buyerPhone;
        private String gatewayBuyerUuid;
        private Money amount;
        private final Set<PaymentMethod> paymentMethods = EnumSet.noneOf(PaymentMethod.class);
        private String redirectUrl;
        private String cancelUrl;
        private String webhookUrl;
        private Address billing;
        private Address shipping;
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
         * Sets the buyer's ID in the merchant's system. Needed later for stored cards; leave unset for guest checkout.
         *
         * @param value the ID
         * @return this builder
         */
        public Builder buyerUserId(String value) {
            this.buyerUserId = value;
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
         * Sets the gateway buyer UUID returned on the buyer's first order, so the page can offer their stored cards.
         *
         * @param value the UUID
         * @return this builder
         */
        public Builder gatewayBuyerUuid(String value) {
            this.gatewayBuyerUuid = value;
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
         * Adds payment methods.
         *
         * @param methods the methods to offer
         * @return this builder
         */
        public Builder paymentMethods(PaymentMethod... methods) {
            this.paymentMethods.addAll(List.of(methods));
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
         * Sets the billing address.
         *
         * @param value the address
         * @return this builder
         */
        public Builder billing(Address value) {
            this.billing = value;
            return this;
        }

        /**
         * Sets the shipping address.
         *
         * @param value the address
         * @return this builder
         */
        public Builder shipping(Address value) {
            this.shipping = value;
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
        public CreateOrderRequest build() {
            return new CreateOrderRequest(this);
        }
    }
}
