package dev.bryrich.credapp.onboarding.xlsx;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxReaderTest {

    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** Written the way Excel writes: shared strings, styles deciding what a number means, cell refs. */
    @Test
    void readsCellsTheWayExcelStoresThem() throws Exception {
        Map<String, String> parts = workbookParts("""
                <sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
                  <row r="3">
                    <c r="A3" s="1"><v>29358</v></c>
                    <c r="B3" s="2"><v>0.25</v></c>
                    <c r="C3"><v>1234567890</v></c>
                    <c r="D3" t="b"><v>1</v></c>
                    <c r="E3" t="e"><v>#N/A</v></c>
                    <c r="F3" t="str"><f>A1&amp;"!"</f><v>Provider ID!</v></c>
                    <c r="G3" t="inlineStr"><is><t>typed inline</t></is></c>
                    <c r="H3" s="3"><v>44927.75</v></c>
                    <c r="AA3" t="s"><v>2</v></c>
                  </row>
                </sheetData>""");

        XlsxWorkbook workbook = XlsxReader.read(zip(parts));

        XlsxSheet sheet = workbook.sheet("providers").orElseThrow();
        assertThat(sheet.rows()).extracting(XlsxRow::number).containsExactly(1, 3);
        XlsxRow header = sheet.rows().get(0);
        assertThat(header.cell(0).text()).isEqualTo("Provider ID");
        assertThat(header.cell(1)).isNull();
        assertThat(header.cell(2).text()).isEqualTo("Rich text");

        XlsxRow row = sheet.rows().get(1);
        assertThat(row.cell(0).kind()).isEqualTo(XlsxCell.Kind.DATE);
        assertThat(row.cell(0).date()).isEqualTo(LocalDate.of(1980, 5, 17));
        assertThat(row.cell(1).kind()).isEqualTo(XlsxCell.Kind.PERCENT);
        assertThat(row.cell(1).number()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(row.cell(2).text()).isEqualTo("1234567890");
        assertThat(row.cell(3).text()).isEqualTo("TRUE");
        assertThat(row.cell(4).kind()).isEqualTo(XlsxCell.Kind.ERROR);
        assertThat(row.cell(5).text()).isEqualTo("Provider ID!");
        assertThat(row.cell(6).text()).isEqualTo("typed inline");
        assertThat(row.cell(7).date()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(row.cell(26).text()).isEqualTo("far column");
    }

    @Test
    void refusesEntityTricksInTheXml() throws Exception {
        Map<String, String> parts = workbookParts("<sheetData/>");
        // The workbook's relationships point its shared strings at this part.
        parts.put("xl/strings.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE sst [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <sst xmlns="%s"><si><t>&secret;</t></si></sst>""".formatted(MAIN));

        assertThatThrownBy(() -> XlsxReader.read(zip(parts)))
                .isInstanceOf(XlsxException.class)
                .hasMessageContaining("looks damaged");
    }

    @Test
    void stopsReadingAFileThatInflatesTooFar() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 45; i++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }
        assertThat(bytes.size()).isLessThan(200_000);

        assertThatThrownBy(() -> XlsxReader.read(new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(XlsxException.class)
                .hasMessageContaining("too large once unpacked");
    }

    @Test
    void saysPlainlyWhenTheFileIsntAWorkbook() {
        assertThatThrownBy(() -> XlsxReader.read(new ByteArrayInputStream("a,b,c\n1,2,3".getBytes())))
                .isInstanceOf(XlsxException.class)
                .hasMessageStartingWith("This isn't an Excel workbook (.xlsx)");
    }

    @Test
    void readsBackWhatTheWriterWrites() throws Exception {
        byte[] file = XlsxWriter.write(List.of(new XlsxWriter.TableSheet("Groups", List.of(
                new XlsxWriter.Column("Group ID", 10, true, XlsxWriter.Format.TEXT, List.of()),
                new XlsxWriter.Column("Name", 10, false, XlsxWriter.Format.GENERAL, List.of("A & B", "<C>"))),
                List.of(List.of("G1", "A & B"), java.util.Arrays.asList(null, "<C>")))));

        XlsxSheet sheet = XlsxReader.read(new ByteArrayInputStream(file)).sheet("Groups").orElseThrow();

        assertThat(sheet.rows()).hasSize(3);
        assertThat(sheet.rows().get(0).cell(0).text()).isEqualTo("Group ID *");
        assertThat(sheet.rows().get(1).cell(1).text()).isEqualTo("A & B");
        assertThat(sheet.rows().get(2).cell(0)).isNull();
        assertThat(sheet.rows().get(2).cell(1).text()).isEqualTo("<C>");
    }

    @Test
    void tellsDateAndPercentFormatsFromPlainNumbers() {
        assertThat(XlsxReader.numberKind(14, null)).isEqualTo(XlsxCell.Kind.DATE);
        assertThat(XlsxReader.numberKind(10, null)).isEqualTo(XlsxCell.Kind.PERCENT);
        assertThat(XlsxReader.numberKind(164, "yyyy\\-mm\\-dd")).isEqualTo(XlsxCell.Kind.DATE);
        assertThat(XlsxReader.numberKind(165, "[$-409]mmmm d, yyyy")).isEqualTo(XlsxCell.Kind.DATE);
        assertThat(XlsxReader.numberKind(166, "0.0%")).isEqualTo(XlsxCell.Kind.PERCENT);
        assertThat(XlsxReader.numberKind(167, "\"Day\" 0")).isEqualTo(XlsxCell.Kind.NUMBER);
        assertThat(XlsxReader.numberKind(168, "#,##0.00")).isEqualTo(XlsxCell.Kind.NUMBER);
        assertThat(XlsxReader.numberKind(0, null)).isEqualTo(XlsxCell.Kind.NUMBER);
    }

    // ============ building workbooks by hand ============

    private static Map<String, String> workbookParts(String sheetData) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        parts.put("xl/workbook.xml", """
                <workbook xmlns="%s" xmlns:r="%s"><sheets>
                <sheet name="Providers" sheetId="1" r:id="rId7"/></sheets></workbook>""".formatted(MAIN, REL));
        parts.put("xl/_rels/workbook.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId7" Type="%s/worksheet" Target="/xl/worksheets/data.xml"/>
                <Relationship Id="rId8" Type="%s/sharedStrings" Target="strings.xml"/>
                <Relationship Id="rId9" Type="%s/styles" Target="styles.xml"/>
                </Relationships>""".formatted(REL, REL, REL));
        parts.put("xl/strings.xml", """
                <sst xmlns="%s">
                <si><t>Provider ID</t></si>
                <si><r><t>Rich </t></r><r><rPr><b/></rPr><t>text</t></r><rPh><t>ignored</t></rPh></si>
                <si><t>far column</t></si>
                </sst>""".formatted(MAIN));
        parts.put("xl/styles.xml", """
                <styleSheet xmlns="%s">
                <numFmts><numFmt numFmtId="170" formatCode="m/d/yyyy h:mm"/></numFmts>
                <cellXfs><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="9"/><xf numFmtId="170"/></cellXfs>
                </styleSheet>""".formatted(MAIN));
        parts.put("xl/worksheets/data.xml", "<worksheet xmlns=\"%s\">%s</worksheet>".formatted(MAIN, sheetData));
        return parts;
    }

    private static ByteArrayInputStream zip(Map<String, String> parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
