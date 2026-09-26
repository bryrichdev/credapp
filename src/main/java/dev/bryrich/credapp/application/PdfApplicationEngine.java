package dev.bryrich.credapp.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/** Fillable PDFs only. Unsupported or ambiguous forms fail before they become templates. */
@Component
public class PdfApplicationEngine {
    public static final int MAX_FIELDS = 250;
    public record Field(String name, String label, String type, boolean required, List<String> options, int maxLength, int page) {}

    public List<Field> inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Choose a fillable PDF no larger than 10 MB");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return inspect(document);
        } catch (IOException ex) {
            throw new IllegalArgumentException("This PDF could not be opened. Use an unencrypted, unsigned fillable PDF.");
        }
    }

    private List<Field> inspect(PDDocument document) throws IOException {
        if (document.isEncrypted() || !document.getSignatureDictionaries().isEmpty())
            throw new IllegalArgumentException("Use an unencrypted, unsigned copy of this PDF");
        if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > 40)
            throw new IllegalArgumentException("Templates must have between 1 and 40 pages");
        rejectActiveContent(document.getDocumentCatalog().getCOSObject(), Collections.newSetFromMap(new IdentityHashMap<>()));
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        if (form == null || form.hasXFA())
            throw new IllegalArgumentException("This file needs standard fillable PDF fields. Scanned, flat and XFA forms are not supported yet.");
        List<Field> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (PDField field : form.getFieldTree()) {
            if (field instanceof PDNonTerminalField) continue;
            if (names.size() >= MAX_FIELDS || !names.add(field.getFullyQualifiedName()))
                throw new IllegalArgumentException("This PDF has too many fields or duplicate field names");
            // Blank signature fields remain interactive for signing outside the app.
            if (field instanceof PDSignatureField) continue;
            // Print and reset buttons hold no answer; sanitize() has already removed their actions.
            if (field instanceof PDPushButton) continue;
            if (field.isReadOnly()) continue;
            String type;
            List<String> options = List.of();
            int maxLength = 4000;
            if (field instanceof PDTextField text) {
                if (text.isPassword() || text.isFileSelect()) throw new IllegalArgumentException("Password and file-selection fields are not supported");
                type = text.isMultiline() ? "multiline" : "text";
                if (text.getMaxLen() > 0) maxLength = Math.min(maxLength, text.getMaxLen());
            } else if (field instanceof PDCheckBox) {
                type = "checkbox";
                options = List.of("Yes", "No");
            } else if (field instanceof PDRadioButton radio) {
                type = "choice";
                options = radio.getExportValues().isEmpty() ? new ArrayList<>(radio.getOnValues()) : radio.getExportValues();
            } else if (field instanceof PDChoice choice && !choice.isMultiSelect()) {
                type = "choice";
                options = choice.getOptionsExportValues();
            } else {
                throw new IllegalArgumentException("Unsupported PDF field: " + field.getFullyQualifiedName() + ". Use text fields, checkboxes or single-choice fields.");
            }
            fields.add(new Field(field.getFullyQualifiedName(), field.getAlternateFieldName() == null
                    ? field.getFullyQualifiedName() : field.getAlternateFieldName(), type, field.isRequired(), List.copyOf(options), maxLength, fieldPage(document, field)));
        }
        for (var page : document.getPages()) {
            var box = page.getCropBox();
            if (box.getWidth() <= 0 || box.getHeight() <= 0 || box.getWidth() > 2000 || box.getHeight() > 2000
                    || box.getWidth() * box.getHeight() > 1_000_000)
                throw new IllegalArgumentException("This PDF has an unsupported page size");
        }
        // Do not silently repair orphaned widgets or accept a field tree disconnected from pages.
        Set<COSDictionary> pageWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var page : document.getPages()) for (var annotation : page.getAnnotations()) {
            if ("Widget".equals(annotation.getSubtype())) pageWidgets.add(annotation.getCOSObject());
        }
        for (PDField field : form.getFieldTree()) if (field instanceof PDTerminalField) {
            for (var widget : field.getWidgets()) {
                if (!pageWidgets.remove(widget.getCOSObject()))
                    throw new IllegalArgumentException("A PDF field is not attached to a page. Repair the form before uploading it.");
            }
        }
        if (!pageWidgets.isEmpty()) throw new IllegalArgumentException("This PDF has fields missing from its field list. Repair the form before uploading it.");
        if (fields.isEmpty()) throw new IllegalArgumentException("This PDF has no editable fields");
        return List.copyOf(fields);
    }

    private int fieldPage(PDDocument document, PDField field) throws IOException {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            var annotations = document.getPage(i).getAnnotations();
            if (annotations.stream().anyMatch(a -> field.getWidgets().stream().anyMatch(w -> w.getCOSObject() == a.getCOSObject()))) return i;
        }
        throw new IllegalArgumentException("A PDF field is not attached to a page");
    }

    /** Numbered overlays let users identify even fields named Text1, Text2, etc. */
    public byte[] preview(byte[] source, int pageNumber) {
        try (PDDocument document = Loader.loadPDF(source)) {
            var fields = inspect(document);
            if (pageNumber < 0 || pageNumber >= document.getNumberOfPages())
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            var page = document.getPage(pageNumber);
            var annotations = page.getAnnotations();
            try (var draw = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page,
                    org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)) {
                for (int i = 0; i < fields.size(); i++) {
                    var field = document.getDocumentCatalog().getAcroForm().getField(fields.get(i).name());
                    for (var widget : field.getWidgets()) {
                        if (annotations.stream().noneMatch(a -> a.getCOSObject() == widget.getCOSObject())) continue;
                        var rect = widget.getRectangle();
                        draw.setStrokingColor(new java.awt.Color(42, 108, 108)); draw.setLineWidth(1.5f);
                        draw.addRect(rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight()); draw.stroke();
                        float x = Math.max(page.getCropBox().getLowerLeftX(), rect.getLowerLeftX() - 24);
                        float y = rect.getUpperRightY() - 12;
                        draw.setNonStrokingColor(new java.awt.Color(42, 108, 108)); draw.addRect(x, y, 22, 13); draw.fill();
                        draw.setNonStrokingColor(java.awt.Color.WHITE); draw.beginText();
                        draw.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 9);
                        draw.newLineAtOffset(x + 2, y + 3); draw.showText(Integer.toString(i + 1)); draw.endText();
                    }
                }
            }
            var output = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(pageNumber, 110), "png", output);
            return output.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("This PDF page could not be previewed"); }
    }

    public int pageCount(byte[] source) {
        try (PDDocument document = Loader.loadPDF(source)) { return document.getNumberOfPages(); }
        catch (IOException e) { throw new IllegalArgumentException("This PDF could not be opened"); }
    }

    /** Actions that only move around the document or open a web link. Everything else is removed. */
    private static final Set<String> SAFE_ACTIONS = Set.of("GoTo", "URI", "Named");

    /**
     * A copy of an uploaded PDF with its scripts and automatic actions removed, ready to store
     * as a template. Forms made in Acrobat usually carry small scripts that format dates and
     * numbers as they're typed; they aren't needed to fill the form, so they're dropped rather
     * than the whole PDF refused, as are buttons' actions and links that open other files or
     * programs. Embedded files still refuse the upload: there's no safe way to keep them.
     * inspect() then checks the result, so anything this misses is refused, not stored.
     */
    public byte[] sanitize(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Choose a fillable PDF no larger than 10 MB");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getSignatureDictionaries().isEmpty())
                throw new IllegalArgumentException("Use an unencrypted, unsigned copy of this PDF");
            COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
            if (catalog.getDictionaryObject(COSName.NAMES) instanceof COSDictionary names) {
                if (names.containsKey(COSName.EMBEDDED_FILES))
                    throw new IllegalArgumentException("Remove embedded files before uploading this PDF");
                names.removeItem(COSName.JAVA_SCRIPT);
            }
            catalog.removeItem(COSName.OPEN_ACTION);
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            // A hybrid form keeps a standard copy of its fields beside the XFA version; use that one.
            if (form != null && form.hasXFA() && form.getFields().iterator().hasNext())
                form.getCOSObject().removeItem(COSName.XFA);
            strip(catalog, Collections.newSetFromMap(new IdentityHashMap<>()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("This PDF could not be opened. Use an unencrypted, unsigned fillable PDF.");
        }
    }

    private void strip(COSBase node, Set<COSBase> visited) {
        if (node == null || !visited.add(node)) return;
        if (visited.size() > 100000) throw new IllegalArgumentException("This PDF is too complex to use as a template");
        if (node instanceof COSObject object) strip(object.getObject(), visited);
        else if (node instanceof COSDictionary dictionary) {
            String type = dictionary.getNameAsString(COSName.S);
            dictionary.removeItem(COSName.AA);
            dictionary.removeItem(COSName.JS);
            // A safe action can chain an unsafe one; the chain goes.
            if (type != null && SAFE_ACTIONS.contains(type)) dictionary.removeItem(COSName.NEXT);
            if (dictionary.getDictionaryObject(COSName.A) instanceof COSDictionary action
                    && !SAFE_ACTIONS.contains(String.valueOf(action.getNameAsString(COSName.S)))) {
                dictionary.removeItem(COSName.A);
            }
            for (COSBase value : new ArrayList<>(dictionary.getValues())) strip(value, visited);
        } else if (node instanceof COSArray array) for (COSBase value : array) strip(value, visited);
    }

    private void rejectActiveContent(COSBase node, Set<COSBase> visited) {
        if (node == null || !visited.add(node)) return;
        if (visited.size() > 100000) throw new IllegalArgumentException("This PDF is too complex to use as a template");
        if (node instanceof COSObject object) rejectActiveContent(object.getObject(), visited);
        else if (node instanceof COSDictionary dictionary) {
            for (String key : List.of("AA", "OpenAction", "JS", "JavaScript", "EmbeddedFiles", "XFA")) {
                if (dictionary.containsKey(COSName.getPDFName(key)))
                    throw new IllegalArgumentException("Remove scripts, automatic actions and embedded files before uploading this PDF");
            }
            String action = dictionary.getNameAsString(COSName.S);
            if (Set.of("JavaScript", "Launch", "SubmitForm", "ImportData", "GoToR", "GoToE").contains(action == null ? "" : action))
                throw new IllegalArgumentException("Remove external actions before uploading this PDF");
            for (COSBase value : dictionary.getValues()) rejectActiveContent(value, visited);
        } else if (node instanceof COSArray array) for (COSBase value : array) rejectActiveContent(value, visited);
    }

    public byte[] fill(byte[] source, Map<String, String> values) {
        try (PDDocument document = Loader.loadPDF(source)) {
            List<Field> fields = inspect(document);
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            form.setNeedAppearances(false);
            for (Field info : fields) {
                String value = values.getOrDefault(info.name(), "").trim();
                if (value.length() > info.maxLength()) throw new IllegalArgumentException(info.label() + " exceeds its maximum length");
                PDField field = form.getField(info.name());
                try {
                    if (field instanceof PDCheckBox check) {
                        if (value.equalsIgnoreCase("Yes")) check.check();
                        else if (value.isEmpty() || value.equalsIgnoreCase("No")) check.unCheck();
                        else throw new IllegalArgumentException();
                    } else if (field instanceof PDChoice choice) {
                        if (value.isEmpty()) choice.setValue(List.of());
                        else {
                            if (!info.options().contains(value)) throw new IllegalArgumentException();
                            choice.setValue(value);
                        }
                    } else if (field instanceof PDRadioButton radio) {
                        if (!value.isEmpty() && !info.options().contains(value)) throw new IllegalArgumentException();
                        radio.setValue(value.isEmpty() ? "Off" : value);
                    } else field.setValue(value);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Check the answer for " + info.label() + ". It must match the field's choices and supported characters.");
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("The PDF could not be filled. Check the template's fields and fonts.");
        }
    }
}
