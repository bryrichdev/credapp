package dev.bryrich.credapp.document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The file types a document may be, recognised by their first bytes rather than by what the
 * browser claims. HTML, SVG and scripts are never accepted, and downloads are always sent as
 * attachments, so an upload can't run in someone else's browser.
 */
enum FileKind {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg", "jpeg"),
    HEIC("image/heic", "heic", "heif"),
    TIFF("image/tiff", "tif", "tiff"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    DOC("application/msword", "doc");

    static final String ACCEPTED = "PDF, PNG, JPEG, HEIC, TIFF or Word";

    private final String contentType;
    private final String[] extensions;

    FileKind(String contentType, String... extensions) {
        this.contentType = contentType;
        this.extensions = extensions;
    }

    String contentType() {
        return contentType;
    }

    static Optional<FileKind> detect(String fileName, byte[] head) {
        String extension = extension(fileName);
        for (FileKind kind : values()) {
            if (Arrays.asList(kind.extensions).contains(extension) && kind.matches(head)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] b) {
        return switch (this) {
            case PDF -> startsWith(b, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case PNG -> startsWith(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case JPEG -> startsWith(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case HEIC -> b.length >= 12 && new String(b, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
                    && java.util.Set.of("heic", "heix", "heim", "heis", "mif1", "msf1")
                    .contains(new String(b, 8, 4, StandardCharsets.US_ASCII));
            case TIFF -> startsWith(b, new byte[]{'I', 'I', 0x2A, 0x00}) || startsWith(b, new byte[]{'M', 'M', 0x00, 0x2A});
            case DOCX -> startsWith(b, new byte[]{'P', 'K', 0x03, 0x04});
            case DOC -> startsWith(b, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
