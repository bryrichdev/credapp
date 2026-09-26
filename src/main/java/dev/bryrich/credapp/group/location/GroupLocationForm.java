package dev.bryrich.credapp.group.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupLocationForm {

    @NotBlank(message = "Location name is required")
    private String locationName;

    private String address;

    private String street1;
    private String street2;
    private String city;
    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;
    @Pattern(regexp = "^$|^[0-9]{5}(-[0-9]{4})?$", message = "ZIP must be 5 or 9 digits")
    private String zipCode;

    @AssertTrue(message = "Enter a full address or complete the street, city, state and ZIP fields")
    public boolean isAddressComplete() {
        boolean structured = java.util.stream.Stream.of(street1, street2, city, state, zipCode)
                .anyMatch(v -> blankToNull(v) != null);
        return structured ? java.util.stream.Stream.of(street1, city, state, zipCode)
                .allMatch(v -> blankToNull(v) != null) : blankToNull(address) != null;
    }

    private String faxNumber;
    private String phoneNumber;
    private String handicapAccess;

    /** Comma-separated in the UI; stored as a TEXT[] on the entity. */
    private String languages;

    /** Empty form, for the create screen. */
    public GroupLocationForm() {
    }

    /** Copies a saved location's values in, so the edit screen renders them. */
    public static GroupLocationForm from(GroupLocation location) {
        GroupLocationForm form = new GroupLocationForm();
        form.locationName = location.getLocationName();
        form.address = location.getAddress();
        form.street1 = location.getStreet1();
        form.street2 = location.getStreet2();
        form.city = location.getCity();
        form.state = location.getState();
        form.zipCode = location.getZipCode();

        form.faxNumber = location.getFaxNumber();
        form.phoneNumber = location.getPhoneNumber();
        form.handicapAccess = location.getHandicapAccess();
        form.languages = String.join(", ", location.getLanguages());
        return form;
    }

    public GroupLocation toEntity() {
        GroupLocation location = new GroupLocation(locationName, address);
        applyTo(location);
        return location;
    }

    /** Copies this form's values onto an existing location, for updates. */
    public void applyTo(GroupLocation location) {
        location.setLocationName(locationName);
        location.setAddress(blankToNull(street1) == null ? address :
                java.util.stream.Stream.of(street1, street2, city, state, zipCode)
                        .filter(v -> blankToNull(v) != null).map(String::trim)
                        .collect(java.util.stream.Collectors.joining(", ")));
        location.setStreet1(blankToNull(street1));
        location.setStreet2(blankToNull(street2));
        location.setCity(blankToNull(city));
        location.setState(blankToNull(state) == null ? null : state.trim().toUpperCase(java.util.Locale.ROOT));
        location.setZipCode(blankToNull(zipCode));

        location.setFaxNumber(blankToNull(faxNumber));
        location.setPhoneNumber(blankToNull(phoneNumber));
        location.setHandicapAccess(blankToNull(handicapAccess));
        location.setLanguages(splitList(languages));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** "English, Spanish" -> ["English", "Spanish"]; blank entries dropped. */
    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStreet1() { return street1; }
    public void setStreet1(String value) { street1 = value; }
    public String getStreet2() { return street2; }
    public void setStreet2(String value) { street2 = value; }
    public String getCity() { return city; }
    public void setCity(String value) { city = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String value) { zipCode = value; }

    public String getFaxNumber() {
        return faxNumber;
    }

    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getHandicapAccess() {
        return handicapAccess;
    }

    public void setHandicapAccess(String handicapAccess) {
        this.handicapAccess = handicapAccess;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }
}
