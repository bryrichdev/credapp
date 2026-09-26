package dev.bryrich.credapp.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PdfApplicationEngineTest {
    static { System.setProperty("java.awt.headless", "true"); }
    private final PdfApplicationEngine engine = new PdfApplicationEngine();
    @Test
    void fillsEveryPageAndPreservesCanonicalValuesWidgetParentsAndAppearances() throws Exception {
        byte[] source = TestApplicationPdf.bytes();
        var fields = engine.inspect(source);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(engine.preview(source, 1))).getWidth()).isPositive();
        assertThat(fields).hasSize(7);
        byte[] output = engine.fill(source, Map.of("provider_name", "Priya Shah", "npi", "1234567890", "ssn", "123456789",
                "practice_city", "Grand Rapids", "notes", "Reviewed against provider records.\nSynthetic data only.", "citizen", "Yes", "type", "Initial"));
        try (var doc = Loader.loadPDF(output)) {
            var form = doc.getDocumentCatalog().getAcroForm();
            assertThat(form.getNeedAppearances()).isFalse();
            assertThat(form.getField("provider_name").getValueAsString()).isEqualTo("Priya Shah");
            assertThat(form.getField("practice_city").getValueAsString()).isEqualTo("Grand Rapids");
            assertThat(((PDCheckBox)form.getField("citizen")).isChecked()).isTrue();
            assertThat(((PDChoice) form.getField("type")).getValue()).containsExactly("Initial");
            for (var field : form.getFieldTree()) for (var widget : field.getWidgets()) {
                assertThat(widget.getAppearance()).isNotNull();
                assertThat(widget.getAppearance().getNormalAppearance()).isNotNull();
                if (widget.getCOSObject() != field.getCOSObject()) assertThat(widget.getCOSObject().getDictionaryObject(COSName.PARENT)).isEqualTo(field.getCOSObject());
            }
            Path dir = Path.of("target/pdf-qa"); Files.createDirectories(dir); Files.write(dir.resolve("reviewed-application.pdf"), output);
            Files.write(dir.resolve("blank-template.pdf"), source);
            var renderer = new PDFRenderer(doc);
            for (int page = 0; page < doc.getNumberOfPages(); page++) ImageIO.write(renderer.renderImageWithDPI(page, 110), "png", dir.resolve("page-" + (page + 1) + ".png").toFile());
        }
    }
    @Test
    void rejectsFlatMalformedActiveAndOrphanedForms() throws Exception {
        assertThatThrownBy(() -> engine.inspect("not a PDF".getBytes())).isInstanceOf(IllegalArgumentException.class);
        try (var doc = new PDDocument()) { doc.addPage(new PDPage()); assertThatThrownBy(() -> engine.inspect(save(doc))).hasMessageContaining("fillable"); }
        try (var doc = Loader.loadPDF(TestApplicationPdf.bytes())) {
            doc.getDocumentCatalog().getCOSObject().setString(COSName.getPDFName("JS"), "bad");
            assertThatThrownBy(() -> engine.inspect(save(doc))).hasMessageContaining("scripts");
        }
        try (var doc = Loader.loadPDF(TestApplicationPdf.bytes())) {
            doc.getDocumentCatalog().getAcroForm().getFields().removeFirst();
            assertThatThrownBy(() -> engine.inspect(save(doc))).hasMessageContaining("missing from its field list");
        }
    }
    @Test
    void invalidChoiceDoesNotProduceAnApparentlySuccessfulPdf() throws Exception {
        assertThatThrownBy(() -> engine.fill(TestApplicationPdf.bytes(), Map.of("citizen", "perhaps")))
                .hasMessageContaining("US citizen");
        assertThatThrownBy(() -> engine.fill(TestApplicationPdf.bytes(), Map.of("type", "not an option")))
                .hasMessageContaining("Application type");
    }
    @Test
    void sanitizeStripsScriptsAndAutomaticActionsSoAcrobatFormsCanBeUsed() throws Exception {
        byte[] scripted = withScripts(TestApplicationPdf.bytes());
        assertThatThrownBy(() -> engine.inspect(scripted)).hasMessageContaining("scripts");
        byte[] clean = engine.sanitize(scripted);
        assertThat(engine.inspect(clean)).hasSize(7).noneMatch(f -> f.name().equals("print"));
        assertThat(new String(clean, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("JavaScript", "AFDate");
        try (var doc = Loader.loadPDF(clean)) {
            assertThat(doc.getDocumentCatalog().getCOSObject().containsKey(COSName.OPEN_ACTION)).isFalse();
        }
        assertThat(engine.fill(clean, Map.of("provider_name", "Priya Shah"))).isNotEmpty();
    }
    @Test
    void sanitizeRefusesEmbeddedFilesAndStripsLaunchActions() throws Exception {
        try (var doc = Loader.loadPDF(TestApplicationPdf.bytes())) {
            var names = new org.apache.pdfbox.cos.COSDictionary();
            names.setItem(COSName.EMBEDDED_FILES, new org.apache.pdfbox.cos.COSDictionary());
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
            byte[] bytes = save(doc);
            assertThatThrownBy(() -> engine.sanitize(bytes)).hasMessageContaining("embedded files");
        }
        try (var doc = Loader.loadPDF(TestApplicationPdf.bytes())) {
            doc.getDocumentCatalog().getAcroForm().getField("npi").getWidgets().getFirst().getCOSObject()
                    .setItem(COSName.A, action("Launch", null));
            byte[] clean = engine.sanitize(save(doc));
            assertThat(new String(clean, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("/Launch");
            assertThat(engine.inspect(clean)).hasSize(7);
        }
    }

    /** The scripts forms made in Acrobat carry: date formatting, a print button, an open action. */
    static byte[] withScripts(byte[] source) throws Exception {
        try (var doc = Loader.loadPDF(source)) {
            var form = doc.getDocumentCatalog().getAcroForm();
            var widget = form.getField("npi").getWidgets().getFirst().getCOSObject();
            var aa = new org.apache.pdfbox.cos.COSDictionary();
            aa.setItem(COSName.F, action("JavaScript", "AFDate_FormatEx(\"mm/dd/yyyy\");"));
            aa.setItem(COSName.K, action("JavaScript", "AFDate_KeystrokeEx(\"mm/dd/yyyy\");"));
            widget.setItem(COSName.AA, aa);
            var print = new PDPushButton(form); print.setPartialName("print"); print.setAlternateFieldName("Print form");
            form.getFields().add(print);
            var pw = print.getWidgets().getFirst(); var page = doc.getPage(0);
            pw.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(400, 700, 80, 20)); pw.setPage(page);
            pw.getCOSObject().setItem(COSName.A, action("JavaScript", "this.print();"));
            page.getAnnotations().add(pw);
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.OPEN_ACTION, action("JavaScript", "app.alert('hi');"));
            var names = new org.apache.pdfbox.cos.COSDictionary();
            names.setItem(COSName.JAVA_SCRIPT, new org.apache.pdfbox.cos.COSDictionary());
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
            return save(doc);
        }
    }
    private static org.apache.pdfbox.cos.COSDictionary action(String type, String js) {
        var action = new org.apache.pdfbox.cos.COSDictionary();
        action.setName(COSName.S, type);
        if (js != null) action.setString(COSName.JS, js);
        return action;
    }
    private static byte[] save(PDDocument doc) throws Exception { var out = new ByteArrayOutputStream(); doc.save(out); return out.toByteArray(); }
}
