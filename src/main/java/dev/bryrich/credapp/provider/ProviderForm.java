package dev.bryrich.credapp.provider;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ProviderForm {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dob;

    private String placeOfBirth;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "NPI must be exactly 10 digits")
    private String npi;

    /** Write-only: never load a stored SSN into an edit form. Blank preserves it. */
    @Pattern(regexp = "^$|^[0-9]{9}$", message = "SSN must be exactly 9 digits")
    private String ssn;

    public String getSsn() { return ssn; }
    /** People type 123-45-6789 or 123 45 6789; only the digits are kept. */
    public void setSsn(String ssn) { this.ssn = ssn == null ? null : ssn.replaceAll("[\\s-]", ""); }

    private Sex sex;

    private String phoneNumber;

    @Email(message = "Enter a valid email address")
    private String emailAddress;

    private String street1;
    private String street2;
    private String city;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;

    @Pattern(regexp = "^$|^[0-9]{5}(-[0-9]{4})?$", message = "ZIP must be 5 or 9 digits")
    private String zipCode;

    private Boolean usCitizen;
    private String ecfmg;
    private String degree;
    private String schoolName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate graduationDate;

    private String caqhId;
    private String caqhUsername;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fluShotDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate tbTestDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate caqhAttestedDate;

    /** The four array columns are entered as comma-separated text and split on save. */
    private String prevNames;
    private String languages;
    private String modalities;
    private String areasOfExpertise;


    /** Empty form, for the create screen. */
    public ProviderForm() {
    }

    /** Copies a saved provider's values in, so the edit screen renders them. */
    public static ProviderForm from(Provider provider) {
        ProviderForm form = new ProviderForm();
        form.firstName = provider.getFirstName();
        form.lastName = provider.getLastName();
        form.dob = provider.getDob();
        form.placeOfBirth = provider.getPlaceOfBirth();
        form.npi = provider.getNpi();
        form.sex = provider.getSex();
        form.phoneNumber = provider.getPhoneNumber();
        form.emailAddress = provider.getEmailAddress();
        form.street1 = provider.getStreet1();
        form.street2 = provider.getStreet2();
        form.city = provider.getCity();
        form.state = provider.getState();
        form.zipCode = provider.getZipCode();
        form.usCitizen = provider.getUsCitizen();
        form.ecfmg = provider.getEcfmg();
        form.degree = provider.getDegree();
        form.schoolName = provider.getSchoolName();
        form.graduationDate = provider.getGraduationDate();
        form.caqhId = provider.getCaqhId();
        form.caqhUsername = provider.getCaqhUsername();
        form.fluShotDate = provider.getFluShotDate();
        form.tbTestDate = provider.getTbTestDate();
        form.caqhAttestedDate = provider.getCaqhAttestedDate();
        form.prevNames = joinList(provider.getPrevNames());
        form.languages = joinList(provider.getLanguages());
        form.modalities = joinList(provider.getModalities());
        form.areasOfExpertise = joinList(provider.getAreasOfExpertise());
        return form;
    }

    public Provider toEntity() {
        Provider provider = new Provider(firstName, lastName);
        applyTo(provider);
        return provider;
    }

    /** Copies this form's values onto an existing provider, for updates. */
    public void applyTo(Provider provider) {
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setDob(dob);
        provider.setPlaceOfBirth(blankToNull(placeOfBirth));
        provider.setNpi(blankToNull(npi));
        if (blankToNull(ssn) != null) provider.setSsn(ssn);
        provider.setSex(sex);
        provider.setPhoneNumber(blankToNull(phoneNumber));
        provider.setEmailAddress(blankToNull(emailAddress));
        provider.setStreet1(blankToNull(street1));
        provider.setStreet2(blankToNull(street2));
        provider.setCity(blankToNull(city));
        provider.setState(upperOrNull(state));
        provider.setZipCode(blankToNull(zipCode));
        provider.setUsCitizen(usCitizen);
        provider.setEcfmg(blankToNull(ecfmg));
        provider.setDegree(blankToNull(degree));
        provider.setSchoolName(blankToNull(schoolName));
        provider.setGraduationDate(graduationDate);
        provider.setCaqhId(blankToNull(caqhId));
        provider.setCaqhUsername(blankToNull(caqhUsername));
        provider.setFluShotDate(fluShotDate);
        provider.setTbTestDate(tbTestDate);
        provider.setCaqhAttestedDate(caqhAttestedDate);
        provider.setPrevNames(splitList(prevNames));
        provider.setLanguages(splitList(languages));
        provider.setModalities(splitList(modalities));
        provider.setAreasOfExpertise(splitList(areasOfExpertise));
    }

    /**
     * caqhSecretRef is deliberately absent. It points at a secret held outside the
     * database, and nothing entered through a form belongs in it.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** "English, Spanish" -> ["English", "Spanish"]; blank entries dropped. */
    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String joinList(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getStreet1() { return street1; }
    public void setStreet1(String street1) { this.street1 = street1; }

    public String getStreet2() { return street2; }
    public void setStreet2(String street2) { this.street2 = street2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public Boolean getUsCitizen() { return usCitizen; }
    public void setUsCitizen(Boolean usCitizen) { this.usCitizen = usCitizen; }

    public String getEcfmg() { return ecfmg; }
    public void setEcfmg(String ecfmg) { this.ecfmg = ecfmg; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public LocalDate getGraduationDate() { return graduationDate; }
    public void setGraduationDate(LocalDate graduationDate) { this.graduationDate = graduationDate; }

    public String getCaqhId() { return caqhId; }
    public void setCaqhId(String caqhId) { this.caqhId = caqhId; }

    public String getCaqhUsername() { return caqhUsername; }
    public void setCaqhUsername(String caqhUsername) { this.caqhUsername = caqhUsername; }

    public LocalDate getFluShotDate() { return fluShotDate; }
    public void setFluShotDate(LocalDate fluShotDate) { this.fluShotDate = fluShotDate; }

    public LocalDate getTbTestDate() { return tbTestDate; }
    public void setTbTestDate(LocalDate tbTestDate) { this.tbTestDate = tbTestDate; }
    public LocalDate getCaqhAttestedDate() { return caqhAttestedDate; }
    public void setCaqhAttestedDate(LocalDate caqhAttestedDate) { this.caqhAttestedDate = caqhAttestedDate; }

    public String getPrevNames() { return prevNames; }
    public void setPrevNames(String prevNames) { this.prevNames = prevNames; }

    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    public String getModalities() { return modalities; }
    public void setModalities(String modalities) { this.modalities = modalities; }

    public String getAreasOfExpertise() { return areasOfExpertise; }
    public void setAreasOfExpertise(String areasOfExpertise) { this.areasOfExpertise = areasOfExpertise; }

}
