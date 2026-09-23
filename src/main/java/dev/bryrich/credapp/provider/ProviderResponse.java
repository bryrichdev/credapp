package dev.bryrich.credapp.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProviderResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate dob,
        String npi,
        Sex sex,
        String phoneNumber,
        String emailAddress,
        String street1,
        String street2,
        String city,
        String state,
        String zipCode,
        Boolean usCitizen,
        String ecfmg,
        String degree,
        String schoolName,
        LocalDate graduationDate,
        String caqhId,
        String caqhUsername,
        List<String> prevNames,
        List<String> languages,
        List<String> modalities,
        List<String> areasOfExpertise,
        LocalDate fluShotDate,
        LocalDate tbTestDate,
        Instant createdAt
) {
    public static ProviderResponse from(Provider p) {
        return new ProviderResponse(p.getId(), p.getFirstName(), p.getLastName(),
                p.getDob(), p.getNpi(), p.getSex(), p.getPhoneNumber(), p.getEmailAddress(),
                p.getStreet1(), p.getStreet2(), p.getCity(), p.getState(), p.getZipCode(),
                p.getUsCitizen(), p.getEcfmg(), p.getDegree(), p.getSchoolName(),
                p.getGraduationDate(), p.getCaqhId(), p.getCaqhUsername(),
                p.getPrevNames(), p.getLanguages(), p.getModalities(), p.getAreasOfExpertise(),
                p.getFluShotDate(), p.getTbTestDate(), p.getCreatedAt());
    }
}
