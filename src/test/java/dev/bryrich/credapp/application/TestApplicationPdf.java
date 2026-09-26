package dev.bryrich.credapp.application;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.*;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import java.io.*;

final class TestApplicationPdf {
    static byte[] bytes() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            var form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            var resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 12 Tf 0 g");
            var first = new PDPage(); var second = new PDPage();
            doc.addPage(first); doc.addPage(second);
            title(doc, first, "SYNTHETIC PAYER APPLICATION - TEST ONLY");
            title(doc, second, "APPLICATION DETAILS - TEST ONLY");
            text(doc, form, first, "provider_name", "Provider name", 650, true);
            text(doc, form, first, "npi", "NPI", 565, true);
            text(doc, form, first, "ssn", "SSN", 480, false);
            text(doc, form, second, "practice_city", "Practice city", 650, true);
            var notes = text(doc, form, second, "notes", "Application notes", 565, false);
            notes.setMultiline(true);
            notes.getWidgets().getFirst().setRectangle(new PDRectangle(50, 480, 490, 75));
            var check = new PDCheckBox(form); check.setPartialName("citizen"); check.setAlternateFieldName("US citizen");
            form.getFields().add(check);
            var widget = check.getWidgets().getFirst(); widget.setRectangle(new PDRectangle(50, 415, 20, 20)); widget.setPage(second);
            var appearance = new PDAppearanceDictionary(); var states = new org.apache.pdfbox.cos.COSDictionary();
            for (String value : new String[]{"Off", "Yes"}) {
                var stream = new PDAppearanceStream(doc); stream.setBBox(new PDRectangle(20,20)); stream.setResources(resources);
                try (var draw = new PDPageContentStream(doc, stream)) {
                    draw.addRect(1,1,18,18); draw.stroke();
                    if (value.equals("Yes")) { draw.moveTo(4,10); draw.lineTo(8,5); draw.lineTo(16,16); draw.stroke(); }
                }
                states.setItem(COSName.getPDFName(value), stream);
            }
            appearance.setNormalAppearance(new PDAppearanceEntry(states)); widget.setAppearance(appearance); check.unCheck();
            second.getAnnotations().add(widget);
            try (var draw = new PDPageContentStream(doc, second, PDPageContentStream.AppendMode.APPEND, true)) { label(draw, "US citizen", 80, 420); }
            var choice = new PDComboBox(form); choice.setPartialName("type"); choice.setAlternateFieldName("Application type");
            choice.setOptions(java.util.List.of("Initial", "Recredentialing")); form.getFields().add(choice);
            var cw = choice.getWidgets().getFirst(); cw.setRectangle(new PDRectangle(50, 340, 300, 30)); cw.setPage(second); second.getAnnotations().add(cw);
            try (var draw = new PDPageContentStream(doc, second, PDPageContentStream.AppendMode.APPEND, true)) { label(draw, "Application type", 50, 380); }
            ByteArrayOutputStream output = new ByteArrayOutputStream(); doc.save(output); return output.toByteArray();
        }
    }
    private static PDTextField text(PDDocument doc, PDAcroForm form, PDPage page, String name, String label, float y, boolean required) throws IOException {
        PDTextField field = new PDTextField(form); field.setPartialName(name); field.setAlternateFieldName(label); field.setRequired(required);
        form.getFields().add(field); var widget = field.getWidgets().getFirst(); widget.setRectangle(new PDRectangle(50, y - 40, 490, 30));
        widget.setPage(page); page.getAnnotations().add(widget);
        try (var draw = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            label(draw, label, 50, y); draw.setStrokingColor(.7f); draw.addRect(50,y - 40,490,30); draw.stroke();
        }
        return field;
    }
    private static void title(PDDocument doc, PDPage page, String text) throws IOException {
        try (var draw = new PDPageContentStream(doc, page)) { label(draw, text, 50, 730); }
    }
    private static void label(PDPageContentStream draw, String text, float x, float y) throws IOException {
        draw.beginText(); draw.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12); draw.newLineAtOffset(x,y); draw.showText(text); draw.endText();
    }
}
