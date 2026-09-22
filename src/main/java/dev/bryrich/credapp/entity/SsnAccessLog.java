package dev.bryrich.credapp.entity;

import dev.bryrich.credapp.entity.enums.SsnSubjectType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per decryption of a stored SSN. Nothing here points at another table on
 * purpose — the subject and the viewer are snapshotted, so the trail survives their
 * deletion. Rows are written, never updated.
 */
@Entity
@Table(name = "ssn_access_log")
public class SsnAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private SsnSubjectType subjectType;

    @Column(nullable = false, updatable = false)
    private Long subjectId;

    /** The subject's name as it read when it was viewed. */
    @Column(nullable = false, updatable = false)
    private String subjectName;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String userEmail;

    @Column(updatable = false)
    private String ipAddress;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant accessedAt;

    protected SsnAccessLog() {}

    public SsnAccessLog(SsnSubjectType subjectType, Long subjectId, String subjectName,
                        Long userId, String userEmail, String ipAddress) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.userId = userId;
        this.userEmail = userEmail;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public SsnSubjectType getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }
}
