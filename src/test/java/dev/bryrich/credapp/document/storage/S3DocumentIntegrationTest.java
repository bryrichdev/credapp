package dev.bryrich.credapp.document.storage;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.document.DocumentService;
import dev.bryrich.credapp.document.DocumentType;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroupWipeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Documents in a workspace's own bucket, and their cleanup, against S3Mock. LocalStack now needs an account token,
 * so it isn't used. S3Mock doesn't check IAM, so the data role's tag rule is covered by the
 * policy in aws/storage.tf, not here.
 */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.storage.mode=s3",
        "credapp.storage.bucket-prefix=credcloud-test-ws-",
        "credapp.storage.region=us-east-1"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class S3DocumentIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final byte[] PDF = "%PDF-1.7\nA copy of a DEA certificate\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withExposedPorts(9090);

    static {
        // S3Mock accepts any signature; the SDK just needs something to sign with.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry registry) {
        registry.add("credapp.storage.endpoint", S3DocumentIntegrationTest::endpoint);
    }

    @Autowired DocumentService documents;
    @Autowired RegistrationService registration;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentFileCleanup cleanup;
    @Autowired UserGroupWipeService wipes;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anUploadGoesToTheWorkspacesBucketEncrypted_andOpensAndDeletesFromThere() {
        User admin = register();
        as(admin);
        imports.importFile(TestWorkbook.fullPractice().bytes());
        long workspace = admin.getUserGroupId();
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);

        long id = documents.upload(workspace, DocumentService.Owner.PROVIDER, provider, DocumentType.OTHER,
                "DEA", "dea.pdf", PDF, null, admin.getEmail());

        assertThat(jdbc.queryForObject("SELECT stored_in FROM documents WHERE id = ?", String.class, id)).isEqualTo("s3");
        assertThat(jdbc.queryForObject("SELECT content IS NULL FROM documents WHERE id = ?", Boolean.class, id)).isTrue();
        String bucket = "credcloud-test-ws-" + workspace;
        byte[] stored = s3Client().getObjectAsBytes(r -> r.bucket(bucket).key("documents/" + id)).asByteArray();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).as("encrypted before upload").doesNotContain("DEA certificate");

        var download = documents.open(workspace, id, admin.getId(), admin.getEmail(), "127.0.0.1").orElseThrow();
        assertThat(download.content()).isEqualTo(PDF);

        assertThat(documents.delete(workspace, id)).isPresent();
        assertThat(queued(workspace)).as("queued by the trigger").isEqualTo(1);
        cleanup.run();
        assertThat(queued(workspace)).isZero();
        assertThatThrownBy(() -> s3Client().getObject(r -> r.bucket(bucket).key("documents/" + id)))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void wipingAWorkspaceDeletesItsFilesToo() {
        User admin = register();
        as(admin);
        imports.importFile(TestWorkbook.fullPractice().bytes());
        long workspace = admin.getUserGroupId();
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        long group = jdbc.queryForObject("SELECT min(id) FROM groups WHERE user_group_id = ?", Long.class, workspace);
        long first = documents.upload(workspace, DocumentService.Owner.PROVIDER, provider, DocumentType.OTHER,
                null, "a.pdf", PDF, null, admin.getEmail());
        long second = documents.upload(workspace, DocumentService.Owner.GROUP, group, DocumentType.OTHER,
                null, "b.pdf", PDF, null, admin.getEmail());
        SecurityContextHolder.clearContext();

        wipes.wipe(workspace, "test");

        assertThat(queued(workspace)).isEqualTo(2);
        cleanup.run();
        String bucket = "credcloud-test-ws-" + workspace;
        assertThat(s3Client().listObjectsV2(r -> r.bucket(bucket)).contents()).isEmpty();
        assertThat(queued(workspace)).isZero();
        assertThat(first).isNotEqualTo(second);
    }

    private long queued(long workspace) {
        return jdbc.queryForObject("SELECT count(*) FROM document_file_deletions WHERE workspace_id = ?", Long.class, workspace);
    }

    private static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }

    private static S3Client s3Client() {
        return S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint())).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build();
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("S3 documents " + UUID.randomUUID());
        return registration.register(form);
    }

    private static void as(User actor) {
        var principal = new CredAppUserDetails(actor);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
