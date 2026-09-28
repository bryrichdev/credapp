package dev.bryrich.credapp.document.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Bucket setup and object keys, against mocked S3 clients. */
class WorkspaceFilesTest {

    private static final byte[] BYTES = {1, 2, 3};

    // The SDK's builder-style methods are default methods that build a request and call the
    // request-object method. Run those for real so the verifications below see real requests.
    private static S3Client s3() {
        Answer<Object> answer = call -> call.getArguments().length > 0 && call.getArgument(0) instanceof Consumer
                ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call);
        return mock(S3Client.class, withSettings().defaultAnswer(answer));
    }

    private final S3Client admin = s3();
    private final S3Client data = s3();
    private final WorkspaceFiles files = new WorkspaceFiles("credcloud-1-ws-", Region.US_EAST_1, admin, ws -> data);

    @Test
    void theFirstUploadMakesTheBucketOnce_withVersionsKept30Days() {
        files.put(7, 42, BYTES);
        files.put(7, 43, BYTES);

        ArgumentCaptor<CreateBucketRequest> create = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(admin, times(1)).createBucket(create.capture());
        assertThat(create.getValue().bucket()).isEqualTo("credcloud-1-ws-7");
        assertThat(create.getValue().createBucketConfiguration()).as("us-east-1 takes no location").isNull();

        ArgumentCaptor<PutBucketVersioningRequest> versioning = ArgumentCaptor.forClass(PutBucketVersioningRequest.class);
        verify(admin).putBucketVersioning(versioning.capture());
        assertThat(versioning.getValue().versioningConfiguration().status()).isEqualTo(BucketVersioningStatus.ENABLED);

        ArgumentCaptor<PutBucketLifecycleConfigurationRequest> lifecycle =
                ArgumentCaptor.forClass(PutBucketLifecycleConfigurationRequest.class);
        verify(admin).putBucketLifecycleConfiguration(lifecycle.capture());
        var rule = lifecycle.getValue().lifecycleConfiguration().rules().getFirst();
        assertThat(rule.id()).isEqualTo(WorkspaceFiles.NONCURRENT_RULE);
        assertThat(rule.noncurrentVersionExpiration().noncurrentDays()).isEqualTo(30);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(data, times(2)).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getAllValues()).extracting(PutObjectRequest::bucket, PutObjectRequest::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("credcloud-1-ws-7", "documents/42"),
                        org.assertj.core.groups.Tuple.tuple("credcloud-1-ws-7", "documents/43"));
        verify(admin, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void aBucketFromAnEarlierRunIsSetUpAgain() {
        when(admin.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyOwnedByYouException.builder().message("yours").build());

        files.put(7, 42, BYTES);

        verify(admin).putBucketVersioning(any(PutBucketVersioningRequest.class));
        verify(admin).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
        verify(data).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void otherRegionsNameTheirLocation() {
        WorkspaceFiles oregon = new WorkspaceFiles("p-", Region.US_WEST_2, admin, ws -> data);

        oregon.put(3, 1, BYTES);

        ArgumentCaptor<CreateBucketRequest> create = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(admin).createBucket(create.capture());
        assertThat(create.getValue().createBucketConfiguration().locationConstraintAsString()).isEqualTo("us-west-2");
    }

    @Test
    void readsAndDeletesUseTheWorkspacesOwnBucket() {
        when(data.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), BYTES));

        assertThat(files.get(9, 5)).isEqualTo(BYTES);
        files.delete(9, 5);

        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(data).getObjectAsBytes(get.capture());
        assertThat(get.getValue().bucket()).isEqualTo("credcloud-1-ws-9");
        assertThat(get.getValue().key()).isEqualTo("documents/5");
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(data).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo("documents/5");
    }

    @Test
    void settingsAreCheckedAtStartup() {
        assertThat(new WorkspaceFiles("database", "", "", "us-east-1", "").enabled()).isFalse();
        assertThatThrownBy(() -> new WorkspaceFiles("s3", "", "", "us-east-1", ""))
                .hasMessageContaining("bucket-prefix");
        assertThatThrownBy(() -> new WorkspaceFiles("dropbox", "", "", "us-east-1", ""))
                .hasMessageContaining("database or s3");
    }
}
