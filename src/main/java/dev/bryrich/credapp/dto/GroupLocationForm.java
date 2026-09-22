package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupLocation;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupLocationForm {

    @NotBlank(message = "Location name is required")
    private String locationName;

    @NotBlank(message = "Address is required")
    private String address;

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
        location.setAddress(address);
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
