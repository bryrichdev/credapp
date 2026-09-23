package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.license.License;
import dev.bryrich.credapp.ssn.SsnConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "providers")
public class Provider extends GroupScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dob;
    private String placeOfBirth;
    private String npi;

    /** Stored as AES-256-GCM ciphertext; see {@link SsnConverter}. */
    @Convert(converter = SsnConverter.class)
    private String ssn;
    private Sex sex;
    private String phoneNumber;
    private String emailAddress;

    /** Maiden and other former names, for primary source verification. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> prevNames = new ArrayList<>();

    private Boolean usCitizen;

    /** ECFMG certificate number, for graduates of non-US medical schools. */
    private String ecfmg;

    private String degree;

    @Column(name = "street_1")
    private String street1;

    @Column(name = "street_2")
    private String street2;

    private String city;
    private String state;
    private String zipCode;

    private String schoolName;
    private LocalDate graduationDate;

    private String caqhId;
    private String caqhUsername;

    /**
     * Reference to the CAQH password held outside the database, not the password itself.
     * Nothing in the application should ever write a credential into this column.
     */
    private String caqhSecretRef;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> languages = new ArrayList<>();

    private LocalDate fluShotDate;
    private LocalDate tbTestDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> modalities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> areasOfExpertise = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<License> licenses = new ArrayList<>();

    protected Provider() {}

    public Provider(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public List<String> getPrevNames() {
        return prevNames;
    }

    public void setPrevNames(List<String> prevNames) {
        this.prevNames = prevNames == null ? new ArrayList<>() : new ArrayList<>(prevNames);
    }

    public Boolean getUsCitizen() {
        return usCitizen;
    }

    public void setUsCitizen(Boolean usCitizen) {
        this.usCitizen = usCitizen;
    }

    public String getEcfmg() {
        return ecfmg;
    }

    public void setEcfmg(String ecfmg) {
        this.ecfmg = ecfmg;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    /** Single-line rendering for tables and PDF fields that take one address box. */
    public String getFormattedAddress() {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, street1);
        addIfPresent(parts, street2);
        addIfPresent(parts, city);
        addIfPresent(parts, Stream.of(state, zipCode)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" ")));
        return String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public LocalDate getGraduationDate() {
        return graduationDate;
    }

    public void setGraduationDate(LocalDate graduationDate) {
        this.graduationDate = graduationDate;
    }

    public String getCaqhId() {
        return caqhId;
    }

    public void setCaqhId(String caqhId) {
        this.caqhId = caqhId;
    }

    public String getCaqhUsername() {
        return caqhUsername;
    }

    public void setCaqhUsername(String caqhUsername) {
        this.caqhUsername = caqhUsername;
    }

    public String getCaqhSecretRef() {
        return caqhSecretRef;
    }

    public void setCaqhSecretRef(String caqhSecretRef) {
        this.caqhSecretRef = caqhSecretRef;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages == null ? new ArrayList<>() : new ArrayList<>(languages);
    }

    public LocalDate getFluShotDate() {
        return fluShotDate;
    }

    public void setFluShotDate(LocalDate fluShotDate) {
        this.fluShotDate = fluShotDate;
    }

    public LocalDate getTbTestDate() {
        return tbTestDate;
    }

    public void setTbTestDate(LocalDate tbTestDate) {
        this.tbTestDate = tbTestDate;
    }

    public List<String> getModalities() {
        return modalities;
    }

    public void setModalities(List<String> modalities) {
        this.modalities = modalities == null ? new ArrayList<>() : new ArrayList<>(modalities);
    }

    public List<String> getAreasOfExpertise() {
        return areasOfExpertise;
    }

    public void setAreasOfExpertise(List<String> areasOfExpertise) {
        this.areasOfExpertise = areasOfExpertise == null ? new ArrayList<>() : new ArrayList<>(areasOfExpertise);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<License> getLicenses() {
        return licenses;
    }

    public void addLicense(License license) {
        licenses.add(license);
        license.setProvider(this);
    }

    public void removeLicense(License license) {
        licenses.remove(license);
        license.setProvider(null);
    }
}