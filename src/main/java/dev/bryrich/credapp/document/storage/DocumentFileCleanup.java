package dev.bryrich.credapp.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.util.List;

/**
 * Deletes S3 files whose documents rows are gone. A database trigger queues them in
 * document_file_deletions (V20), whichever way the rows went. Runs every minute. With
 * versioning on, a deleted file can still be recovered from the bucket for 30 days.
 */
@Component
public class DocumentFileCleanup {

    private static final Logger log = LoggerFactory.getLogger(DocumentFileCleanup.class);

    /** After this many failures a file is left for a person to look at; see last_error. */
    static final int MAX_ATTEMPTS = 20;

    private record Queued(long id, long workspace, long document) {
    }

    private final JdbcTemplate jdbc;
    private final WorkspaceFiles files;

    public DocumentFileCleanup(JdbcTemplate jdbc, WorkspaceFiles files) {
        this.jdbc = jdbc;
        this.files = files;
    }

    /** @return how many files it deleted */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${credapp.storage.cleanup-every:PT1M}")
    public int run() {
        List<Queued> queued = jdbc.query("""
                        SELECT id, workspace_id, document_id FROM document_file_deletions
                        WHERE attempts < ? ORDER BY id LIMIT 100""",
                (row, i) -> new Queued(row.getLong("id"), row.getLong("workspace_id"), row.getLong("document_id")),
                MAX_ATTEMPTS);
        int deleted = 0;
        for (Queued file : queued) {
            try {
                files.delete(file.workspace(), file.document());
            } catch (NoSuchBucketException gone) {
                // Nothing left to delete.
            } catch (RuntimeException e) {
                String error = String.valueOf(e.getMessage());
                jdbc.update("UPDATE document_file_deletions SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                        error.length() > 1000 ? error.substring(0, 1000) : error, file.id());
                log.warn("Couldn't delete the file for document {} in workspace {}: {}",
                        file.document(), file.workspace(), error);
                continue;
            }
            jdbc.update("DELETE FROM document_file_deletions WHERE id = ?", file.id());
            deleted++;
        }
        if (deleted > 0) {
            log.info("Deleted {} document files from S3", deleted);
        }
        return deleted;
    }
}
