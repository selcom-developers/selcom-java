package io.github.selcomdevelopers.selcom;

import io.github.selcomdevelopers.selcom.api.Address;
import io.github.selcomdevelopers.selcom.api.CreateOrderMinimalRequest;
import io.github.selcomdevelopers.selcom.api.CreateOrderRequest;
import io.github.selcomdevelopers.selcom.api.PaymentMethod;
import io.github.selcomdevelopers.selcom.api.Requests;
import io.github.selcomdevelopers.selcom.result.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Made-up test data and one typed call per operation. */
final class Samples {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T07:30:46Z"), ZoneOffset.UTC);

    private Samples() {
    }

    static SelcomConfig config() {
        return SelcomConfig.builder()
                .environment(Environment.SANDBOX)
                .credentials("test-key-0001", "test-secret-0001")
                .vendor("TILL123")
                .pin("4321")
                .build();
    }

    static SelcomClient client(FakeTransport transport) {
        return SelcomClient.builder(config()).transport(transport).clock(CLOCK).build();
    }

    static Address address() {
        return new Address("Test", "Buyer", "1 Test Street", null, "Dar es Salaam", "Dar es Salaam", "11101", "TZ", "255700000001");
    }

    static CreateOrderRequest createOrder() {
        return CreateOrderRequest.builder()
                .orderId("ORD-1001")
                .buyerEmail("test.buyer@example.com")
                .buyerName("Test Buyer")
                .buyerPhone("255700000001")
                .amount(Money.tzs(8000))
                .paymentMethods(PaymentMethod.ALL)
                .redirectUrl("https://shop.example.com/return")
                .webhookUrl("https://shop.example.com/selcom/webhook/abc123")
                .billing(address())
                .noOfItems(1)
                .build();
    }

    static CreateOrderMinimalRequest createOrderMinimal() {
        return CreateOrderMinimalRequest.builder()
                .orderId("ORD-1002")
                .buyerEmail("test.buyer@example.com")
                .buyerName("Test Buyer")
                .buyerPhone("255700000001")
                .amount(Money.tzs(5000))
                .noOfItems(1)
                .build();
    }

    /** Calls the typed method for an operation with a valid sample request. */
    static Result<?> call(SelcomClient client, Operation operation) {
        return switch (operation) {
            case CHECKOUT_CREATE_ORDER -> client.createOrder(createOrder());
            case CHECKOUT_CREATE_ORDER_MINIMAL -> client.createOrderMinimal(createOrderMinimal());
            case CHECKOUT_WALLET_PAYMENT -> client.walletPayment(new Requests.WalletPayment("TXN-WLT-0001", "ORD-1002", "255700000001"));
            case CHECKOUT_CARD_PAYMENT -> client.cardPayment(new Requests.CardPayment("TXN-CRD-0001", "ORD-1001", "tok-test-0001", "USER-1", "GBU-TEST-0001"));
            case CHECKOUT_ORDER_STATUS -> client.orderStatus(new Requests.OrderStatus("ORD-1001"));
            case CHECKOUT_CANCEL_ORDER -> client.cancelOrder(new Requests.CancelOrder("ORD-1001"));
            case CHECKOUT_LIST_ORDERS -> client.listOrders(new Requests.ListOrders(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 21)));
            case CHECKOUT_STORED_CARDS -> client.storedCards(new Requests.StoredCards("GBU-TEST-0001", "USER-1"));
            case CHECKOUT_DELETE_CARD -> client.deleteCard(new Requests.DeleteCard("CARD-1", "GBU-TEST-0001"));
            case UTILITYPAYMENT_PROCESS -> client.utilityPayment(new Requests.UtilityPayment("TXN-UTL-0001", "LUKU", "01234567890", Money.tzs(5000), null));
            case UTILITYPAYMENT_LOOKUP -> client.utilityLookup(new Requests.UtilityLookup("LUKU", "01234567890", "TXN-LKP-0001"));
            case UTILITYPAYMENT_QUERY -> client.utilityQuery(new Requests.UtilityQuery("TXN-UTL-0001"));
            case WALLETCASHIN_PROCESS -> client.walletCashIn(new Requests.WalletCashIn("TXN-CSH-0001", "CASHIN", "255700000002", Money.tzs(10000), null));
            case WALLETCASHIN_NAMELOOKUP -> client.walletNameLookup(new Requests.WalletNameLookup("CASHIN", "255700000002", "TXN-NML-0001"));
            case WALLETCASHIN_QUERY -> client.walletCashInQuery(new Requests.WalletCashInQuery("TXN-CSH-0001"));
            case VENDOR_BALANCE -> client.vendorBalance(new Requests.VendorBalance("TXN-BAL-0001"));
        };
    }
}
