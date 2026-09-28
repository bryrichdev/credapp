package dev.bryrich.credapp.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.Tag;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Document files in S3, one bucket per workspace, made on the first upload.
 *
 * <p>The app's own AWS identity can create and set up these buckets but can't read or write
 * what's in them. For that it assumes the data role with the session tagged
 * {@code workspace=<id>}, and the role only allows the bucket whose name ends in that id. So a
 * wrong id in a key can't reach another workspace's files. See aws/storage.tf.
 *
 * <p>Buckets keep old versions for 30 days, so a deleted or replaced file can be recovered.
 * New buckets block public access and disable ACLs by default, so nothing here turns that on.
 */
@Component
public class WorkspaceFiles {

    static final String NONCURRENT_RULE = "recover-for-30-days";

    private final boolean enabled;
    private final String bucketPrefix;
    private final Region region;
    private final LongFunction<S3Client> workspaceClients;
    private final Map<Long, S3Client> clients = new ConcurrentHashMap<>();
    private final Set<Long> ready = ConcurrentHashMap.newKeySet();
    private final Supplier<S3Client> adminFactory;
    private volatile S3Client admin;

    public WorkspaceFiles(@Value("${credapp.storage.mode:database}") String mode,
                          @Value("${credapp.storage.bucket-prefix:}") String bucketPrefix,
                          @Value("${credapp.storage.data-role-arn:}") String dataRoleArn,
                          @Value("${credapp.storage.region:us-east-1}") String region,
                          @Value("${credapp.storage.endpoint:}") String endpoint) {
        this.enabled = switch (mode.trim().toLowerCase()) {
            case "database" -> false;
            case "s3" -> true;
            default -> throw new IllegalStateException("credapp.storage.mode must be database or s3, not " + mode);
        };
        if (enabled && bucketPrefix.isBlank()) {
            throw new IllegalStateException("Set credapp.storage.bucket-prefix to store documents in S3");
        }
        this.bucketPrefix = bucketPrefix;
        this.region = Region.of(region);
        URI endpointUri = endpoint.isBlank() ? null : URI.create(endpoint);
        AwsCredentialsProvider base = DefaultCredentialsProvider.builder().build();
        this.adminFactory = () -> build(base, endpointUri);
        StsClient sts = enabled && !dataRoleArn.isBlank()
                ? StsClient.builder().region(this.region).credentialsProvider(base).build() : null;
        this.workspaceClients = workspace -> build(
                sts == null ? base : assumeDataRole(sts, dataRoleArn, workspace), endpointUri);
    }

    /** For tests: clients supplied directly. */
    WorkspaceFiles(String bucketPrefix, Region region, S3Client admin, LongFunction<S3Client> workspaceClients) {
        this.enabled = true;
        this.bucketPrefix = bucketPrefix;
        this.region = region;
        this.adminFactory = () -> admin;
        this.workspaceClients = workspaceClients;
    }

    /** Whether new uploads go to S3. Files already in the database stay readable either way. */
    public boolean enabled() {
        return enabled;
    }

    public String bucket(long workspace) {
        return bucketPrefix + workspace;
    }

    static String key(long documentId) {
        return "documents/" + documentId;
    }

    public void put(long workspace, long documentId, byte[] encrypted) {
        ensureBucket(workspace);
        client(workspace).putObject(r -> r.bucket(bucket(workspace)).key(key(documentId)), RequestBody.fromBytes(encrypted));
    }

    public byte[] get(long workspace, long documentId) {
        return client(workspace).getObjectAsBytes(r -> r.bucket(bucket(workspace)).key(key(documentId))).asByteArray();
    }

    /** With versioning on, this leaves a delete marker; the file itself goes after 30 days. */
    public void delete(long workspace, long documentId) {
        client(workspace).deleteObject(r -> r.bucket(bucket(workspace)).key(key(documentId)));
    }

    /** Creates and sets up the workspace's bucket once per run of the app. Safe to repeat. */
    void ensureBucket(long workspace) {
        if (ready.contains(workspace)) {
            return;
        }
        synchronized (this) {
            if (ready.contains(workspace)) {
                return;
            }
            String bucket = bucket(workspace);
            S3Client s3 = admin();
            try {
                s3.createBucket(r -> {
                    r.bucket(bucket);
                    if (!Region.US_EAST_1.equals(region)) {
                        r.createBucketConfiguration(c -> c.locationConstraint(region.id()));
                    }
                });
            } catch (BucketAlreadyOwnedByYouException alreadyMade) {
                // Made by an earlier run; set it up again below in case that run stopped halfway.
            }
            s3.putBucketVersioning(r -> r.bucket(bucket)
                    .versioningConfiguration(v -> v.status(BucketVersioningStatus.ENABLED)));
            s3.putBucketLifecycleConfiguration(r -> r.bucket(bucket).lifecycleConfiguration(l -> l.rules(
                    LifecycleRule.builder()
                            .id(NONCURRENT_RULE)
                            .status(ExpirationStatus.ENABLED)
                            .filter(f -> f.prefix(""))
                            .noncurrentVersionExpiration(n -> n.noncurrentDays(30))
                            .abortIncompleteMultipartUpload(a -> a.daysAfterInitiation(1))
                            .build())));
            ready.add(workspace);
        }
    }

    private S3Client admin() {
        S3Client client = admin;
        if (client == null) {
            synchronized (this) {
                if (admin == null) {
                    admin = adminFactory.get();
                }
                client = admin;
            }
        }
        return client;
    }

    private S3Client client(long workspace) {
        return clients.computeIfAbsent(workspace, workspaceClients::apply);
    }

    private S3Client build(AwsCredentialsProvider credentials, URI endpoint) {
        S3ClientBuilder builder = S3Client.builder().region(region).credentialsProvider(credentials);
        if (endpoint != null) {
            builder.endpointOverride(endpoint).forcePathStyle(true);
        }
        return builder.build();
    }

    /** Short-lived credentials for one workspace's bucket, refreshed before they expire. */
    private static AwsCredentialsProvider assumeDataRole(StsClient sts, String roleArn, long workspace) {
        return StsAssumeRoleCredentialsProvider.builder()
                .stsClient(sts)
                .refreshRequest(r -> r.roleArn(roleArn)
                        .roleSessionName("credcloud-ws-" + workspace)
                        .durationSeconds(3600)
                        .tags(Tag.builder().key("workspace").value(Long.toString(workspace)).build()))
                .build();
    }
}
