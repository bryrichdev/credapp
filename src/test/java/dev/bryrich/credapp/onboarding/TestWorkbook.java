package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.OnboardingTemplate.Column;
import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.CERTIFICATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.CLAIMS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.DISCLOSURES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_LOCATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_MEMBERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_SPECIALTIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.HOSPITAL_PRIVILEGES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERSHIP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNER_RELATIONSHIPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYER_CONTACTS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PRACTICE_LOCATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_SPECIALTIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.REFERENCES;

/**
 * Builds a filled-in onboarding workbook for tests: the real template's sheets and headers,
 * with rows given as header/value pairs. Values are written as text, the way a person
 * typing into Excel's text-formatted cells would leave them.
 */
public final class TestWorkbook {

    private final Map<Sheet, List<Map<String, String>>> rows = new LinkedHashMap<>();
    private final List<XlsxWriter.TableSheet> extraSheets = new ArrayList<>();

    /** pairs: header, value, header, value... Headers are the template's, without the " *". */
    public TestWorkbook row(Sheet sheet, String... pairs) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            row.put(pairs[i], pairs[i + 1]);
        }
        rows.computeIfAbsent(sheet, key -> new ArrayList<>()).add(row);
        return this;
    }

    /** A sheet with any headers at all, for testing what the parser does with the unexpected. */
    public TestWorkbook rawSheet(String name, List<String> headers, List<List<String>> values) {
        extraSheets.add(new XlsxWriter.TableSheet(name, headers.stream()
                .map(header -> new XlsxWriter.Column(header, 12, false, XlsxWriter.Format.GENERAL, List.of()))
                .toList(), values));
        return this;
    }

    public byte[] bytes() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        for (Sheet sheet : OnboardingTemplate.SHEETS) {
            List<Column> columns = sheet.columns();
            List<List<String>> values = new ArrayList<>();
            for (Map<String, String> row : rows.getOrDefault(sheet, List.of())) {
                for (String header : row.keySet()) {
                    if (columns.stream().noneMatch(column -> column.header().equals(header))) {
                        throw new IllegalArgumentException(sheet.name() + " has no column " + header);
                    }
                }
                values.add(columns.stream().map(column -> row.get(column.header())).toList());
            }
            sheets.add(new XlsxWriter.TableSheet(sheet.name(), columns.stream()
                    .map(column -> new XlsxWriter.Column(column.header(), column.width(), column.required(),
                            column.type().format(), column.type().choices()))
                    .toList(), values));
        }
        sheets.addAll(extraSheets);
        return XlsxWriter.write(sheets);
    }

    /**
     * Two groups and two providers with a row on every sheet, so every group-scoped table
     * gets at least one record.
     */
    public static TestWorkbook fullPractice() {
        return new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "DBA", "Lakeside",
                        "NPI", "1234567893", "Tax ID", "12-3456789")
                .row(GROUPS, "Group ID", "g2", "Legal business name", "North Surgery PC", "Tax ID", "987654321")
                .row(GROUP_LOCATIONS, "Location ID", "L1", "Group ID", "G1", "Location name", "Main office",
                        "Address", "1 Main St, Toledo, OH 43604", "Phone", "419-555-0100")
                .row(GROUP_LOCATIONS, "Location ID", "L2", "Group ID", "G2", "Location name", "North",
                        "Address", "5 North Rd, Toledo, OH 43612")
                .row(OWNERS, "Owner ID", "O1", "First name", "Ann", "Last name", "Lee",
                        "Date of birth", "2/3/1970", "SSN", "123-45-6789")
                .row(OWNERS, "Owner ID", "O2", "First name", "Bob", "Last name", "Lee")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O1", "Percent owned", "60",
                        "Effective date", "2020-01-01")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O2", "Percent owned", "40")
                .row(OWNERSHIP, "Group ID", "G2", "Owner ID", "O1", "Percent owned", "100")
                .row(OWNER_RELATIONSHIPS, "Group ID", "G1", "Owner ID", "O1", "Relationship", "Spouse",
                        "Related owner ID", "O2")
                .row(GROUP_SPECIALTIES, "Group ID", "G1", "Taxonomy code", "207q00000x", "Primary", "Yes")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah",
                        "Date of birth", "1980-05-17", "Sex", "F", "NPI", "1111111111", "Email", "priya@example.com",
                        "ZIP", "02134", "US citizen", "Y", "Languages", "English, Hindi")
                .row(PROVIDERS, "Provider ID", "P2", "First name", "Tom", "Last name", "Ng", "NPI", "2222222222")
                .row(GROUP_MEMBERS, "Provider ID", "P1", "Group ID", "G1", "Effective date", "3/1/2021")
                .row(GROUP_MEMBERS, "Provider ID", "P2", "Group ID", "G1")
                .row(GROUP_MEMBERS, "Provider ID", "P2", "Group ID", "G2")
                .row(PRACTICE_LOCATIONS, "Provider ID", "P1", "Location ID", "L1", "PCP or SCP", "PCP")
                .row(PRACTICE_LOCATIONS, "Provider ID", "P2", "Location ID", "L2", "PCP or SCP", "Specialist")
                .row(PROVIDER_SPECIALTIES, "Provider ID", "P1", "Taxonomy code", "207QA0401X", "Primary", "Yes")
                .row(PROVIDER_SPECIALTIES, "Provider ID", "P2", "Taxonomy code", "207L00000X")
                .row(LICENSES, "Provider ID", "P1", "State", "oh", "License number", "35.123456", "License type", "MD",
                        "Expiration date", "2027-01-31", "Status", "active")
                .row(LICENSES, "Provider ID", "P2", "State", "OH", "License number", "35.654321", "License type", "MD",
                        "Expiration date", "2026-12-31")
                .row(CERTIFICATIONS, "Provider ID", "P1", "Board", "American Board of Family Medicine",
                        "Effective date", "2015-07-01")
                .row(HOSPITAL_PRIVILEGES, "Provider ID", "P2", "Hospital", "St. Vincent", "Status", "Courtesy",
                        "Admitting provider ID", "P1")
                .row(POLICIES, "Policy ID", "POL1", "Provider ID", "P1", "Policy number", "PX-1", "Carrier", "MedPro",
                        "Type of coverage", "Claims-made", "Effective date", "2024-01-01",
                        "Expiration date", "2025-01-01", "Coverage per occurrence", "$1,000,000",
                        "Shared or individual", "Individual")
                .row(POLICIES, "Policy ID", "POL2", "Group ID", "G1", "Policy number", "GX-1", "Carrier", "MedPro",
                        "Type of coverage", "Occurrence", "Effective date", "2024-01-01",
                        "Shared or individual", "Shared")
                .row(CLAIMS, "Provider ID", "P1", "Claim number", "C-1", "Carrier", "MedPro", "Policy ID", "POL1",
                        "Outcome", "Closed without payment")
                .row(CLAIMS, "Provider ID", "P2", "Claim number", "C-2", "Carrier", "MedPro", "Policy ID", "pol2")
                .row(REFERENCES, "Provider ID", "P1", "Name", "Dr. Jane Kim", "Relationship", "Colleague",
                        "Email", "jane.kim@example.com")
                .row(DISCLOSURES, "Provider ID", "P2", "Classification", "Misdemeanor", "Status", "Dismissed",
                        "Incident date", "2010-01-01")
                .row(PAYERS, "Payer ID", "PAY1", "Name", "Aetna", "Note", "Commercial")
                .row(PAYERS, "Payer ID", "PAY2", "Name", "Cigna")
                .row(PAYER_CONTACTS, "Contact ID", "C1", "Payer ID", "PAY1", "Group ID", "G1", "Name", "Jane Doe",
                        "Role", "Account rep", "Phone", "800-555-0100", "Email", "jane.doe@aetna.test")
                .row(PAYER_CONTACTS, "Payer ID", "PAY2", "Name", "Credentialing line", "Role", "Provider services")
                .row(PAYER_CONTACTS, "Payer ID", "PAY1", "Provider ID", "P1", "Role", "Provider's analyst")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1", "Status", "Participating",
                        "Payer-assigned ID", "G-88213", "Effective date", "2024-01-01", "Account rep ID", "C1")
                .row(GROUP_PAYERS, "Group ID", "G2", "Payer ID", "PAY2", "Status", "In progress",
                        "Submitted date", "2025-08-15", "Notes", "Waiting on the W-9")
                .row(PROVIDER_PAYERS, "Provider ID", "P1", "Payer ID", "PAY1", "Status", "Submitted",
                        "Submitted date", "2025-09-02")
                .row(PROVIDER_PAYERS, "Provider ID", "P2", "Payer ID", "PAY2");
    }
}
