package io.github.selcomdevelopers.selcom.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A billing or shipping address. Sent as dotted keys ({@code billing.firstname}), never as a nested object.
 *
 * @param firstname first name
 * @param lastname last name
 * @param address1 first address line
 * @param address2 second address line, or null
 * @param city city
 * @param stateOrRegion state or region
 * @param postcodeOrPobox postcode or P.O. box
 * @param country ISO 3166-1 alpha-2 code, for example {@code TZ}
 * @param phone phone number
 */
public record Address(
        String firstname,
        String lastname,
        String address1,
        String address2,
        String city,
        String stateOrRegion,
        String postcodeOrPobox,
        String country,
        String phone) {

    /** Validates the required parts. */
    public Address {
        Requests.require(firstname, "firstname");
        Requests.require(lastname, "lastname");
        Requests.require(address1, "address1");
        Requests.require(city, "city");
        Requests.require(stateOrRegion, "stateOrRegion");
        Requests.require(postcodeOrPobox, "postcodeOrPobox");
        Requests.require(country, "country");
        Requests.require(phone, "phone");
    }

    Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("firstname", firstname);
        out.put("lastname", lastname);
        out.put("address_1", address1);
        if (address2 != null) {
            out.put("address_2", address2);
        }
        out.put("city", city);
        out.put("state_or_region", stateOrRegion);
        out.put("postcode_or_pobox", postcodeOrPobox);
        out.put("country", country);
        out.put("phone", phone);
        return out;
    }

    /** Leaves out the personal data. */
    @Override
    public String toString() {
        return "Address[country=" + country + ", city=" + city + "]";
    }
}
