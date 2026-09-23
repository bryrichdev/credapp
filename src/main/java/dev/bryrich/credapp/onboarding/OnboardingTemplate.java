package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.license.LicenseStatus;
import dev.bryrich.credapp.malpractice.CoverageScope;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.Cell;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.Style;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.TextRow;
import dev.bryrich.credapp.owner.Relationship;
import dev.bryrich.credapp.payer.enrollment.EnrollmentStatus;
import dev.bryrich.credapp.provider.Sex;
import dev.bryrich.credapp.provider.disclosure.ChargeClassification;
import dev.bryrich.credapp.provider.disclosure.ChargeStatus;
import dev.bryrich.credapp.provider.location.PcpScp;
import dev.bryrich.credapp.provider.privilege.PrivilegeStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The onboarding workbook: which sheets it has, their columns, and what goes in each. The
 * template people download, its instructions sheet, and the upload parser all read from
 * here, so they can't drift apart.
 *
 * A column either binds straight to a property of the matching profile-form row (its key is
 * that property's name) or is an ID column that links rows across sheets.
 */
public final class OnboardingTemplate {

    /** Keys of the ID and link columns. Every other key is a form property name. */
    public static final String ID = "@id";
    public static final String GROUP = "@group";
    public static final String OWNER = "@owner";
    public static final String RELATED_OWNER = "@relatedOwner";
    public static final String PROVIDER = "@provider";
    public static final String LOCATION = "@location";
    public static final String POLICY = "@policy";
    public static final String ADMITTING_PROVIDER = "@admittingProvider";
    public static final String PAYER = "@payer";
    public static final String ACCOUNT_REP = "@accountRep";

    public record Column(String key, String header, ValueType type, boolean required, int width, String note) {

        public boolean binds() {
            return !key.startsWith("@");
        }

        /** The header without the " *" the template adds to required columns. */
        public boolean matchesHeader(String text) {
            return normalize(text).equals(normalize(header));
        }

        static String normalize(String text) {
            return text.replace("*", "").trim().replaceAll("\\s+", " ").toLowerCase();
        }
    }

    public record Sheet(String name, String purpose, List<Column> columns) {

        public Optional<Column> column(String key) {
            return columns.stream().filter(column -> column.key().equals(key)).findFirst();
        }

        /** The header a form property is shown under, for messages. */
        public String headerFor(String key) {
            return column(key).map(Column::header).orElse(null);
        }
    }

    private OnboardingTemplate() {
    }

    // ============ column shorthands ============

    private static Column required(String key, String header, ValueType type, int width, String note) {
        return new Column(key, header, type, true, width, note);
    }

    private static Column optional(String key, String header, ValueType type, int width, String note) {
        return new Column(key, header, type, false, width, note);
    }

    private static Column ownId(String example) {
        return required(ID, idHeader(example), ValueType.KEY, 12,
                "Make up a short ID for each row, such as " + example + "1. Other sheets use it to point here.");
    }

    private static Column link(String key, String header, boolean required, String sheet) {
        return new Column(key, header, ValueType.KEY, required, 12, "An ID from the " + sheet + " sheet");
    }

    private static String idHeader(String example) {
        return switch (example) {
            case "G" -> "Group ID";
            case "L" -> "Location ID";
            case "O" -> "Owner ID";
            case "P" -> "Provider ID";
            case "PAY" -> "Payer ID";
            case "C" -> "Contact ID";
            default -> "Policy ID";
        };
    }

    private static List<Column> address(String subject) {
        return List.of(
                optional("street1", "Street", ValueType.TEXT, 26, subject + " street address"),
                optional("street2", "Street line 2", ValueType.TEXT, 16, "Suite, unit or floor"),
                optional("city", "City", ValueType.TEXT, 16, null),
                optional("state", "State", ValueType.STATE, 8, null),
                optional("zipCode", "ZIP", ValueType.ZIP, 11, null));
    }

    private static List<Column> concat(List<Column> first, List<Column> second) {
        List<Column> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    // ============ the sheets ============

    public static final Sheet GROUPS = new Sheet("Groups",
            "One row per practice group (the billing entity).", List.of(
            ownId("G"),
            required("lbn", "Legal business name", ValueType.TEXT, 30, "As registered with the IRS"),
            optional("dba", "DBA", ValueType.TEXT, 24, "Doing-business-as name, if different"),
            optional("npi", "NPI", ValueType.digits(10, "Group (type 2) NPI"), 13, null),
            required("taxId", "Tax ID", ValueType.digits(9, "TIN"), 12, null)));

    public static final Sheet GROUP_LOCATIONS = new Sheet("Group Locations",
            "Each place a group sees patients.", List.of(
            ownId("L"),
            link(GROUP, "Group ID", true, "Groups"),
            required("locationName", "Location name", ValueType.TEXT, 24, null),
            required("address", "Address", ValueType.TEXT, 40, "Full street address on one line"),
            optional("phoneNumber", "Phone", ValueType.PHONE, 14, null),
            optional("faxNumber", "Fax", ValueType.PHONE, 14, null),
            optional("handicapAccess", "Handicap access", ValueType.TEXT, 16, "Describe access, or Yes / No"),
            optional("languages", "Languages", ValueType.LIST, 20, "Languages spoken at this location")));

    public static final Sheet OWNERS = new Sheet("Owners",
            "Each person who owns part of a group. List a person once even if they own several groups.",
            concat(List.of(
                    ownId("O"),
                    required("firstName", "First name", ValueType.TEXT, 14, null),
                    required("lastName", "Last name", ValueType.TEXT, 16, null),
                    optional("dob", "Date of birth", ValueType.DATE, 13, null),
                    optional("ssn", "SSN", ValueType.digits(9, "Social Security number"), 12,
                            "Stored encrypted. Leave blank to add it later in CredApp.")),
                    address("Home")));

    public static final Sheet OWNERSHIP = new Sheet("Ownership",
            "Who owns how much of each group. One row per owner per group.", List.of(
            link(GROUP, "Group ID", true, "Groups"),
            link(OWNER, "Owner ID", true, "Owners"),
            optional("percentOwned", "Percent owned", ValueType.PERCENT, 13, "A group's owners can't add up to more than 100"),
            optional("effectiveDate", "Effective date", ValueType.DATE, 13, "When the stake started")));

    public static final Sheet OWNER_RELATIONSHIPS = new Sheet("Owner Relationships",
            "Family ties between owners of the same group. Read a row as: Owner is the Relationship of Related owner.",
            List.of(
                    link(GROUP, "Group ID", true, "Groups"),
                    link(OWNER, "Owner ID", true, "Owners"),
                    required("relationship", "Relationship", ValueType.choice(Relationship.class), 13, null),
                    link(RELATED_OWNER, "Related owner ID", true, "Owners")));

    public static final Sheet GROUP_SPECIALTIES = new Sheet("Group Specialties",
            "A group's taxonomy codes.", List.of(
            link(GROUP, "Group ID", true, "Groups"),
            required("code", "Taxonomy code", ValueType.TAXONOMY, 15, null),
            optional("primary", "Primary", ValueType.YES_NO, 9, "At most one Yes per group")));

    public static final Sheet PROVIDERS = new Sheet("Providers",
            "One row per provider.", concat(concat(List.of(
            ownId("P"),
            required("firstName", "First name", ValueType.TEXT, 14, null),
            required("lastName", "Last name", ValueType.TEXT, 16, null),
            optional("dob", "Date of birth", ValueType.DATE, 13, null),
            optional("placeOfBirth", "Place of birth", ValueType.TEXT, 18, "City and state, or country"),
            optional("sex", "Sex", ValueType.choice(Sex.class, Sex::getLabel,
                    Map.of("M", Sex.MALE, "F", Sex.FEMALE, "X", Sex.OTHER, "U", Sex.UNKNOWN)), 10, null),
            optional("npi", "NPI", ValueType.digits(10, "Individual (type 1) NPI"), 13, null),
            optional("phoneNumber", "Phone", ValueType.PHONE, 14, null),
            optional("emailAddress", "Email", ValueType.TEXT, 24, null)),
            address("Home")), List.of(
            optional("usCitizen", "US citizen", ValueType.YES_NO, 10, null),
            optional("ecfmg", "ECFMG", ValueType.TEXT, 13, "ECFMG certificate number, for international graduates"),
            optional("degree", "Degree", ValueType.TEXT, 10, "MD, DO, NP, PA-C..."),
            optional("schoolName", "Medical school", ValueType.TEXT, 24, null),
            optional("graduationDate", "Graduation date", ValueType.DATE, 14, null),
            optional("caqhId", "CAQH ID", ValueType.TEXT, 12, null),
            optional("caqhUsername", "CAQH username", ValueType.TEXT, 16, null),
            optional("caqhAttestedDate", "CAQH attested date", ValueType.DATE, 15, "When the CAQH profile was last attested"),
            optional("fluShotDate", "Flu shot date", ValueType.DATE, 13, null),
            optional("tbTestDate", "TB test date", ValueType.DATE, 13, null),
            optional("prevNames", "Former names", ValueType.LIST, 18, "Maiden or other former names"),
            optional("languages", "Languages", ValueType.LIST, 18, null),
            optional("modalities", "Modalities", ValueType.LIST, 18, null),
            optional("areasOfExpertise", "Areas of expertise", ValueType.LIST, 22, null))));

    public static final Sheet GROUP_MEMBERS = new Sheet("Group Members",
            "Which groups each provider belongs to. One row per provider per group.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            link(GROUP, "Group ID", true, "Groups"),
            optional("effectiveDate", "Effective date", ValueType.DATE, 13, "When they joined the group")));

    public static final Sheet PRACTICE_LOCATIONS = new Sheet("Practice Locations",
            "Where each provider practices. The location's group must be one of theirs on Group Members.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            link(LOCATION, "Location ID", true, "Group Locations"),
            required("pcpScp", "PCP or SCP", ValueType.choice(PcpScp.class, PcpScp::name, Map.of(
                    "Primary", PcpScp.PCP, "Primary care", PcpScp.PCP,
                    "Specialist", PcpScp.SCP, "Specialty care", PcpScp.SCP)), 11,
                    "PCP for primary care, SCP for specialty care")));

    public static final Sheet PROVIDER_SPECIALTIES = new Sheet("Provider Specialties",
            "A provider's taxonomy codes.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("code", "Taxonomy code", ValueType.TAXONOMY, 15, null),
            optional("primary", "Primary", ValueType.YES_NO, 9, "At most one Yes per provider")));

    public static final Sheet LICENSES = new Sheet("Licenses",
            "State licenses, DEA and similar registrations.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("state", "State", ValueType.STATE, 8, null),
            required("licenseNumber", "License number", ValueType.TEXT, 16, null),
            required("licenseType", "License type", ValueType.TEXT, 16, "MD, DO, RN, DEA..."),
            optional("issueDate", "Issue date", ValueType.DATE, 13, null),
            required("expirationDate", "Expiration date", ValueType.DATE, 14, null),
            optional("status", "Status", ValueType.choice(LicenseStatus.class), 12, "Blank means Active"),
            optional("restrictions", "Restrictions", ValueType.TEXT, 24, null)));

    public static final Sheet CERTIFICATIONS = new Sheet("Certifications",
            "Board certifications.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("board", "Board", ValueType.TEXT, 30, null),
            required("effectiveDate", "Effective date", ValueType.DATE, 13, null),
            optional("expirationDate", "Expiration date", ValueType.DATE, 14, "Blank for a lifetime certification")));

    public static final Sheet HOSPITAL_PRIVILEGES = new Sheet("Hospital Privileges",
            "Hospitals where a provider has privileges.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("name", "Hospital", ValueType.TEXT, 28, null),
            optional("status", "Status", ValueType.choice(PrivilegeStatus.class), 12, "Blank means Active"),
            optional("reappointmentDate", "Reappointment date", ValueType.DATE, 16, "When the hospital next reappoints them"),
            link(ADMITTING_PROVIDER, "Admitting provider ID", false, "Providers")));

    public static final Sheet POLICIES = new Sheet("Malpractice Policies",
            "Malpractice policies. Fill in Provider ID or Group ID, not both.", List.of(
            new Column(ID, "Policy ID", ValueType.KEY, false, 11,
                    "Only needed if a claim points at this policy, such as POL1"),
            link(PROVIDER, "Provider ID", false, "Providers"),
            link(GROUP, "Group ID", false, "Groups"),
            required("policyNumber", "Policy number", ValueType.TEXT, 16, null),
            required("carrierName", "Carrier", ValueType.TEXT, 22, null),
            required("typeOfCoverage", "Type of coverage", ValueType.TEXT, 18, "Claims-made, occurrence..."),
            required("effectiveDate", "Effective date", ValueType.DATE, 13, null),
            optional("expirationDate", "Expiration date", ValueType.DATE, 14, null),
            optional("originalEffectiveDate", "Original effective date", ValueType.DATE, 14, "Prior acts date"),
            optional("amountOfCoveragePerOccurrence", "Coverage per occurrence", ValueType.MONEY, 14, null),
            optional("amountOfCoveragePerAggregate", "Coverage per aggregate", ValueType.MONEY, 14, null),
            required("sharedIndividual", "Shared or individual", ValueType.choice(CoverageScope.class), 13, null)));

    public static final Sheet CLAIMS = new Sheet("Malpractice Claims",
            "Malpractice claims history.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("claimNumber", "Claim number", ValueType.TEXT, 16, null),
            required("carrierName", "Carrier", ValueType.TEXT, 22, null),
            new Column(POLICY, "Policy ID", ValueType.KEY, false, 11,
                    "The provider's own policy or one of their group's, from Malpractice Policies"),
            optional("outcome", "Outcome", ValueType.TEXT, 30, null)));

    public static final Sheet REFERENCES = new Sheet("References",
            "Peer references.", concat(List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("name", "Name", ValueType.TEXT, 22, null),
            optional("title", "Title", ValueType.TEXT, 16, null),
            required("relationship", "Relationship", ValueType.TEXT, 18, "Colleague, supervisor..."),
            optional("emailAddress", "Email", ValueType.TEXT, 24, null),
            optional("phoneNumber", "Phone", ValueType.PHONE, 14, null)),
            address("Mailing")));

    public static final Sheet DISCLOSURES = new Sheet("Disclosures",
            "Criminal charges the provider has disclosed.", List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            required("classification", "Classification", ValueType.choice(ChargeClassification.class), 14, null),
            required("status", "Status", ValueType.choice(ChargeStatus.class), 12, null),
            optional("incidentDate", "Incident date", ValueType.DATE, 13, null),
            optional("dateOfFiling", "Date of filing", ValueType.DATE, 13, null),
            optional("caseNumber", "Case number", ValueType.TEXT, 14, null),
            optional("court", "Court", ValueType.TEXT, 20, null),
            optional("statutoryCitation", "Statutory citation", ValueType.TEXT, 18, null),
            optional("sentencingTerms", "Sentencing terms", ValueType.TEXT, 24, null)));

    // ============ payers ============

    private static final ValueType ENROLLMENT_STATUS = ValueType.choice(EnrollmentStatus.class,
            EnrollmentStatus::getLabel, Map.of(
                    "Participating", EnrollmentStatus.ACTIVE, "Par", EnrollmentStatus.ACTIVE,
                    "Enrolled", EnrollmentStatus.ACTIVE, "Pending", EnrollmentStatus.SUBMITTED));

    private static List<Column> enrollmentColumns() {
        return List.of(
                optional("status", "Status", ENROLLMENT_STATUS, 13, "Blank means Not started"),
                optional("payerAssignedId", "Payer-assigned ID", ValueType.TEXT, 16,
                        "The provider or group number the payer issued"),
                optional("submittedDate", "Submitted date", ValueType.DATE, 14, null),
                optional("effectiveDate", "Effective date", ValueType.DATE, 14, "Required when Active"),
                optional("recredentialDate", "Recredential by", ValueType.DATE, 15,
                        "When the payer recredentials or revalidates"),
                optional("followUpDate", "Follow up on", ValueType.DATE, 13, "A date to chase the payer"),
                optional("notes", "Notes", ValueType.TEXT, 30, null));
    }

    public static final Sheet PAYERS = new Sheet("Payers",
            "Payers the practice is enrolled or enrolling with. A payer already in CredApp with the same name "
                    + "is used as it is; only new payers are added.", List.of(
            ownId("PAY"),
            required("name", "Name", ValueType.TEXT, 26, null),
            optional("note", "Note", ValueType.TEXT, 30, null)));

    public static final Sheet PAYER_CONTACTS = new Sheet("Payer Contacts",
            "People and lines at each payer. Fill in Group ID for a group's designated account rep, or leave "
                    + "Group ID and Provider ID blank for a payer-wide contact.", concat(List.of(
            new Column(ID, "Contact ID", ValueType.KEY, false, 11,
                    "Only needed to name this contact as a group's account rep, such as C1"),
            link(PAYER, "Payer ID", true, "Payers"),
            link(GROUP, "Group ID", false, "Groups"),
            link(PROVIDER, "Provider ID", false, "Providers"),
            optional("name", "Name", ValueType.TEXT, 20, null),
            required("role", "Role", ValueType.TEXT, 20, "Provider rep, credentialing analyst...")), List.of(
            optional("phoneNumber", "Phone", ValueType.PHONE, 14, null),
            optional("faxNumber", "Fax", ValueType.PHONE, 14, null),
            optional("emailAddress", "Email", ValueType.TEXT, 24, null),
            optional("address", "Address", ValueType.TEXT, 30, null))));

    public static final Sheet GROUP_PAYERS = new Sheet("Group Payers",
            "Each group's enrollment with a payer. One row per group per payer.", concat(concat(List.of(
            link(GROUP, "Group ID", true, "Groups"),
            link(PAYER, "Payer ID", true, "Payers")),
            enrollmentColumns()), List.of(
            new Column(ACCOUNT_REP, "Account rep ID", ValueType.KEY, false, 12,
                    "A Contact ID from Payer Contacts: one of this payer's, payer-wide or for this group"))));

    public static final Sheet PROVIDER_PAYERS = new Sheet("Provider Payers",
            "Each provider's enrollment with a payer. One row per provider per payer.", concat(List.of(
            link(PROVIDER, "Provider ID", true, "Providers"),
            link(PAYER, "Payer ID", true, "Payers")),
            enrollmentColumns()));

    /** In template order, which is also the order problems are listed in. */
    public static final List<Sheet> SHEETS = List.of(
            GROUPS, GROUP_LOCATIONS, OWNERS, OWNERSHIP, OWNER_RELATIONSHIPS, GROUP_SPECIALTIES,
            PROVIDERS, GROUP_MEMBERS, PRACTICE_LOCATIONS, PROVIDER_SPECIALTIES, LICENSES,
            CERTIFICATIONS, HOSPITAL_PRIVILEGES, POLICIES, CLAIMS, REFERENCES, DISCLOSURES,
            PAYERS, PAYER_CONTACTS, GROUP_PAYERS, PROVIDER_PAYERS);

    public static final String INSTRUCTIONS = "Instructions";

    public static Optional<Sheet> sheet(String name) {
        return SHEETS.stream().filter(sheet -> sheet.name().equalsIgnoreCase(name.trim())).findFirst();
    }

    // ============ the downloadable file ============

    public static byte[] workbook() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(instructions());
        for (Sheet sheet : SHEETS) {
            sheets.add(new XlsxWriter.TableSheet(sheet.name(), sheet.columns().stream()
                    .map(column -> new XlsxWriter.Column(column.header(), column.width(), column.required(),
                            column.type().format(), column.type().choices()))
                    .toList()));
        }
        return XlsxWriter.write(sheets);
    }

    private static final List<Integer> INSTRUCTION_WIDTHS = List.of(3, 26, 10, 84);

    private static XlsxWriter.TextSheet instructions() {
        List<TextRow> rows = new ArrayList<>();
        rows.add(new TextRow(List.of(new Cell("CredApp onboarding workbook", Style.TITLE)), false, 24));
        rows.add(TextRow.of());
        rows.add(TextRow.of(new Cell("How it works", Style.HEADING)));
        for (String line : List.of(
                "Fill in one sheet per kind of record. Leave a sheet empty if it doesn't apply to you.",
                "Keep row 1 on every sheet as it is: CredApp finds each column by its header. Column order doesn't matter.",
                "Green headers marked * are required on every row you fill in.",
                "Give each group, location, owner, provider and payer a short ID you make up: G1, L1, O1, P1, "
                        + "PAY1 and so on. "
                        + "Other sheets use these IDs to say which record a row belongs to. They only need to be "
                        + "unique within their sheet, and they only mean something inside this file.",
                "Dates can be typed as 2025-01-31 or 1/31/2025. Where a column has a dropdown, pick from it.",
                "Provider SSNs aren't collected here. Add them in CredApp after the import.",
                "Payers already in CredApp are matched by name and used as they are, so list Aetna even if it's "
                        + "on file: its row just gives the other sheets a Payer ID to point at.",
                "To import: in CredApp, open Import, upload this file and check the preview. Nothing is saved until "
                        + "you click Import, and then everything is saved together or not at all.")) {
            rows.add(new TextRow(List.of(new Cell("•", Style.WRAP), Cell.of(line)), true, heightFor(line, 115)));
        }
        for (Sheet sheet : SHEETS) {
            rows.add(TextRow.of());
            rows.add(TextRow.of(new Cell(sheet.name(), Style.HEADING)));
            rows.add(new TextRow(List.of(new Cell("", Style.WRAP), new Cell(sheet.purpose(), Style.MUTED)),
                    true, heightFor(sheet.purpose(), 115)));
            rows.add(TextRow.of(new Cell("", Style.WRAP), new Cell("Column", Style.BOLD),
                    new Cell("Required", Style.BOLD), new Cell("What to enter", Style.BOLD)));
            for (Column column : sheet.columns()) {
                String what = java.util.stream.Stream.of(column.type().describe(), column.note())
                        .filter(part -> part != null && !part.isBlank())
                        .collect(java.util.stream.Collectors.joining(". "));
                rows.add(new TextRow(List.of(new Cell("", Style.WRAP), Cell.of(column.header()),
                        Cell.of(column.required() ? "Yes" : ""), Cell.of(what)), false, heightFor(what, 80)));
            }
        }
        return new XlsxWriter.TextSheet(INSTRUCTIONS, INSTRUCTION_WIDTHS, rows);
    }

    /** Rough row height for wrapped text: 15 points a line at about this many characters a line. */
    private static double heightFor(String text, int charsPerLine) {
        int lines = Math.max(1, (int) Math.ceil(text.length() / (double) charsPerLine));
        return lines == 1 ? 0 : lines * 15 + 2;
    }
}
