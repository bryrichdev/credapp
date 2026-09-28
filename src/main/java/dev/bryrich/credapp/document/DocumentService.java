package dev.bryrich.credapp.document;

import dev.bryrich.credapp.document.storage.WorkspaceFiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Documents for providers and practice groups. Works in plain SQL with the user group always
 * in the WHERE clause, so a document from another group can't be listed, opened or deleted
 * even by guessing its id. The file is encrypted before it's stored and decrypted only to be
 * downloaded, and every download is logged. The encrypted file goes in the row or, when
 * {@link WorkspaceFiles} is on, in the workspace's own S3 bucket; stored_in says which.
 */
@Service
public class DocumentService {

    /** Largest file accepted. Scans and phone photos of a certificate are well under this. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public enum Owner { PROVIDER, GROUP }

    /** A file to download. */
    public record Download(String fileName, String contentType, byte[] content, Owner owner, long ownerId) {
    }

    /** Where a document belongs, for sending people back to the right page. */
    public record Location(Owner owner, long ownerId) {
        public String path() {
            return (owner == Owner.PROVIDER ? "/providers/" : "/groups/") + ownerId + "#documents";
        }
    }

    private final JdbcTemplate jdbc;
    private final DocumentCipher cipher;
    private final WorkspaceFiles files;

    public DocumentService(JdbcTemplate jdbc, DocumentCipher cipher, WorkspaceFiles files) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> list(long userGroupId, Owner owner, long ownerId) {
        return jdbc.query("""
                        SELECT id, doc_type, title, file_name, content_type, size_bytes, expiration_date,
                               uploaded_by, created_at
                        FROM documents WHERE user_group_id = ? AND %s = ?
                        ORDER BY created_at DESC, id DESC""".formatted(column(owner)),
                (row, i) -> new DocumentSummary(row.getLong("id"), DocumentType.fromValue(row.getString("doc_type")),
                        row.getString("title"), row.getString("file_name"), row.getString("content_type"),
                        row.getLong("size_bytes"), row.getObject("expiration_date", LocalDate.class),
                        row.getString("uploaded_by"), row.getTimestamp("created_at").toInstant()),
                userGroupId, ownerId);
    }

    /**
     * Checks and stores an upload.
     *
     * @throws IllegalArgumentException with a message for the page, if the file or its details won't do
     */
    @Transactional
    public long upload(long userGroupId, Owner owner, long ownerId, DocumentType type, String title,
                       String fileName, byte[] content, LocalDate expirationDate, String uploadedBy) {
        if (!ownerExists(userGroupId, owner, ownerId)) {
            throw new IllegalArgumentException("That " + (owner == Owner.PROVIDER ? "provider" : "group") + " doesn't exist");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Choose a file to upload");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("That file is over 10 MB. Scan it at a lower resolution or split it up.");
        }
        String name = cleanName(fileName);
        FileKind kind = FileKind.detect(name, content).orElseThrow(() -> new IllegalArgumentException(
                "That doesn't look like a " + FileKind.ACCEPTED + " file"));
        if (type == null) {
            throw new IllegalArgumentException("Choose what kind of document this is");
        }
        byte[] encrypted = cipher.encrypt(content);
        boolean toS3 = files.enabled();
        Long id = jdbc.queryForObject("""
                        INSERT INTO documents (user_group_id, %s, doc_type, title, file_name, content_type,
                                               size_bytes, content, stored_in, expiration_date, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""".formatted(column(owner)),
                Long.class, userGroupId, ownerId, type.getValue(), blankToNull(title), name, kind.contentType(),
                (long) content.length, new SqlParameterValue(Types.BINARY, toS3 ? null : encrypted),
                toS3 ? "s3" : "database", expirationDate, uploadedBy);
        // Inside the transaction: if S3 refuses the file, the row goes too.
        if (toS3) {
            files.put(userGroupId, id, encrypted);
        }
        return id;
    }

    /** Decrypts a document for download and logs who opened it. */
    @Transactional
    public Optional<Download> open(long userGroupId, long documentId, long userId, String userEmail, String ipAddress) {
        List<Download> found = jdbc.query("""
                        SELECT file_name, content_type, content, stored_in, provider_id, group_id
                        FROM documents WHERE id = ? AND user_group_id = ?""",
                (row, i) -> {
                    long providerId = row.getLong("provider_id");
                    boolean forProvider = !row.wasNull();
                    byte[] encrypted = "s3".equals(row.getString("stored_in"))
                            ? files.get(userGroupId, documentId) : row.getBytes("content");
                    return new Download(row.getString("file_name"), row.getString("content_type"),
                            cipher.decrypt(encrypted),
                            forProvider ? Owner.PROVIDER : Owner.GROUP,
                            forProvider ? providerId : row.getLong("group_id"));
                },
                documentId, userGroupId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        jdbc.update("""
                INSERT INTO document_access_log (user_group_id, document_id, file_name, user_id, user_email, ip_address)
                VALUES (?, ?, ?, ?, ?, ?)""", userGroupId, documentId, found.getFirst().fileName(), userId, userEmail, ipAddress);
        return Optional.of(found.getFirst());
    }

    /**
     * Deletes a document. Its access log stays. A file in S3 is queued for deletion by a
     * trigger and removed by DocumentFileCleanup, like files deleted with their provider.
     */
    @Transactional
    public Optional<Location> delete(long userGroupId, long documentId) {
        return jdbc.query("""
                        DELETE FROM documents WHERE id = ? AND user_group_id = ?
                        RETURNING provider_id, group_id""",
                (row, i) -> {
                    long providerId = row.getLong("provider_id");
                    return row.wasNull() ? new Location(Owner.GROUP, row.getLong("group_id"))
                            : new Location(Owner.PROVIDER, providerId);
                },
                documentId, userGroupId).stream().findFirst();
    }

    private boolean ownerExists(long userGroupId, Owner owner, long ownerId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM %s WHERE id = ? AND user_group_id = ?"
                .formatted(owner == Owner.PROVIDER ? "providers" : "groups"), Long.class, ownerId, userGroupId);
        return count != null && count > 0;
    }

    private static String column(Owner owner) {
        return owner == Owner.PROVIDER ? "provider_id" : "group_id";
    }

    /** Just the name, without any folders a browser sent, and nothing that could break a header. */
    static String cleanName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.length() > 150) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
            name = name.substring(0, 150 - extension.length()) + extension;
        }
        return name.isEmpty() ? "document" : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
