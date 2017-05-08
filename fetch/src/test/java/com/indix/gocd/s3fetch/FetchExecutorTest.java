package com.indix.gocd.s3fetch;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.indix.gocd.utils.Constants;
import com.indix.gocd.utils.Context;
import com.indix.gocd.utils.GoEnvironment;
import com.indix.gocd.utils.TaskExecutionResult;
import com.indix.gocd.utils.mocks.MockContext;
import com.indix.gocd.utils.store.S3ArtifactStore;
import com.indix.gocd.utils.utils.Maps;
import com.thoughtworks.go.plugin.api.response.execution.ExecutionResult;
import com.thoughtworks.go.plugin.api.task.TaskConfig;
import com.thoughtworks.go.plugin.api.task.TaskExecutionContext;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.indix.gocd.utils.Constants.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;


public class FetchExecutorTest {
    private final String destination = "artifacts";
    private final String bucket = "gocd";
    Maps.MapBuilder<String, String> mockEnvironmentVariables;
    private FetchExecutor fetchExecutor;
    private Config config;

    @Before
    public void setUp() throws Exception {
        mockEnvironmentVariables = Maps.<String, String>builder()
                .with(AWS_SECRET_ACCESS_KEY, "secretKey")
                .with(AWS_ACCESS_KEY_ID, "accessId")
                .with(GO_ARTIFACTS_S3_BUCKET, bucket)
                .with(GO_SERVER_DASHBOARD_URL, "http://go.server:8153")
                .with("GO_PACKAGE_GOCD_TESTPUBLISHS3ARTIFACTS_LABEL", "20.1")
                .with("GO_REPO_GOCD_TESTPUBLISHS3ARTIFACTS_S3_BUCKET", bucket)
                .with("GO_PACKAGE_GOCD_TESTPUBLISHS3ARTIFACTS_PIPELINE_NAME", "TestPublish")
                .with("GO_PACKAGE_GOCD_TESTPUBLISHS3ARTIFACTS_STAGE_NAME", "defaultStage")
                .with("GO_PACKAGE_GOCD_TESTPUBLISHS3ARTIFACTS_JOB_NAME", "defaultJob");

        config = new Config(Maps.builder()
                .with(Constants.REPO, Maps.builder().with("value", "GOCD").build())
                .with(Constants.PACKAGE, Maps.builder().with("value", "TESTPUBLISHS3ARTIFACTS").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());

        fetchExecutor = spy(new FetchExecutor());
        doReturn(new GoEnvironment(new HashMap<String,String>())).when(fetchExecutor).getGoEnvironment();
    }

    @Test
    public void shouldBeFailureIfEnvVarNotFound() {
        Map<String, String> mockVariables = mockEnvironmentVariables.with(AWS_ACCESS_KEY_ID, "").build();
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertFalse(result.isSuccessful());
        assertThat(result.message(), is("AWS_ACCESS_KEY_ID environment variable not present"));
    }

    @Test
    public void shouldBeFailureIfFetchConfigNotValid() {
        Map<String, String> mockVariables = mockEnvironmentVariables.build();
        config = new Config(Maps.builder()
                .with(Constants.REPO, Maps.builder().with("value", "Wrong").build())
                .with(Constants.PACKAGE, Maps.builder().with("value", "TESTPUBLISHS3ARTIFACTS").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertFalse(result.isSuccessful());
        assertThat(result.message(), is("Failure while downloading artifacts - Please check Repository name or Package name configuration. Also, ensure that the appropriate S3 material is configured for the pipeline."));
    }

    @Test
    public void shouldBeFailureIfUnableToFetchArtifacts() {
        Map<String, String> mockVariables = mockEnvironmentVariables.build();
        AmazonS3Client mockClient = mockClient();
        doThrow(new AmazonClientException("Exception message")).when(mockClient).listObjects(any(ListObjectsRequest.class));
        doReturn(mockClient).when(fetchExecutor).s3Client(any(GoEnvironment.class));

        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertFalse(result.isSuccessful());
        assertThat(result.message(), is("Failure while downloading artifacts - Exception message"));
    }

    @Test
    public void shouldBeSuccessResultOnSuccessfulFetch() {
        Map<String, String> mockVariables = mockEnvironmentVariables.build();
        AmazonS3Client mockClient = mockClient();
        doReturn(mockClient).when(fetchExecutor).s3Client(any(GoEnvironment.class));
        S3ArtifactStore mockStore = mockStore();

        doReturn(mockStore).when(fetchExecutor).getS3ArtifactStore(any(GoEnvironment.class), any(String.class));
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertTrue(result.isSuccessful());
        assertThat(result.message(), is("Fetched all artifacts"));
        verify(mockStore, times(1)).getPrefix("TestPublish/defaultStage/defaultJob/20.1", "here/artifacts");

    }

    @Test
    public void shouldBeAbleToHandleTaskConfigEntriesWithDashesInTheName() {
        Map<String, String> mockVariables = mockEnvironmentVariables
                .with("GO_PACKAGE_REPO_WITH_DASH_PACKAGE_WITH_DASH_LABEL", "20.1")
                .with("GO_REPO_REPO_WITH_DASH_PACKAGE_WITH_DASH_S3_BUCKET", bucket)
                .with("GO_PACKAGE_REPO_WITH_DASH_PACKAGE_WITH_DASH_PIPELINE_NAME", "TestPublish")
                .with("GO_PACKAGE_REPO_WITH_DASH_PACKAGE_WITH_DASH_STAGE_NAME", "defaultStage")
                .with("GO_PACKAGE_REPO_WITH_DASH_PACKAGE_WITH_DASH_JOB_NAME", "defaultJob")
                .build();
        AmazonS3Client mockClient = mockClient();
        doReturn(mockClient).when(fetchExecutor).s3Client(any(GoEnvironment.class));
        S3ArtifactStore mockStore = mockStore();

        doReturn(mockStore).when(fetchExecutor).getS3ArtifactStore(any(GoEnvironment.class), any(String.class));
        config = new Config(Maps.builder()
                .with(Constants.REPO, Maps.builder().with("value", "repo-with-dash").build())
                .with(Constants.PACKAGE, Maps.builder().with("value", "package-with-dash").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertTrue(result.isSuccessful());
        assertThat(result.message(), is("Fetched all artifacts"));
        verify(mockStore, times(1)).getPrefix("TestPublish/defaultStage/defaultJob/20.1", "here/artifacts");
    }

    @Test
    public void shouldBeAbleToHandleTaskConfigEntriesWithPeriodsInTheName() {
        Map<String, String> mockVariables = mockEnvironmentVariables
                .with("GO_PACKAGE_REPO_WITH_PERIOD_PACKAGE_WITH_PERIOD_LABEL", "20.1")
                .with("GO_REPO_REPO_WITH_PERIOD_PACKAGE_WITH_PERIOD_S3_BUCKET", bucket)
                .with("GO_PACKAGE_REPO_WITH_PERIOD_PACKAGE_WITH_PERIOD_PIPELINE_NAME", "TestPublish")
                .with("GO_PACKAGE_REPO_WITH_PERIOD_PACKAGE_WITH_PERIOD_STAGE_NAME", "defaultStage")
                .with("GO_PACKAGE_REPO_WITH_PERIOD_PACKAGE_WITH_PERIOD_JOB_NAME", "defaultJob")
                .build();
        AmazonS3Client mockClient = mockClient();
        doReturn(mockClient).when(fetchExecutor).s3Client(any(GoEnvironment.class));
        S3ArtifactStore mockStore = mockStore();

        doReturn(mockStore).when(fetchExecutor).getS3ArtifactStore(any(GoEnvironment.class), any(String.class));
        config = new Config(Maps.builder()
                .with(Constants.REPO, Maps.builder().with("value", "repo-with.period").build())
                .with(Constants.PACKAGE, Maps.builder().with("value", "package-with.period").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertTrue(result.isSuccessful());
        assertThat(result.message(), is("Fetched all artifacts"));
        verify(mockStore, times(1)).getPrefix("TestPublish/defaultStage/defaultJob/20.1", "here/artifacts");
    }

    @Test
    public void shouldBeAbleToHandleTaskConfigEntriesWithSpecialCharacters() {
        Map<String, String> mockVariables = mockEnvironmentVariables
                .with("GO_PACKAGE_REPO_WITH________________________________PACKAGE_WITH________________________________LABEL", "20.1")
                .with("GO_REPO_REPO_WITH________________________________PACKAGE_WITH________________________________S3_BUCKET", bucket)
                .with("GO_PACKAGE_REPO_WITH________________________________PACKAGE_WITH________________________________PIPELINE_NAME", "TestPublish")
                .with("GO_PACKAGE_REPO_WITH________________________________PACKAGE_WITH________________________________STAGE_NAME", "defaultStage")
                .with("GO_PACKAGE_REPO_WITH________________________________PACKAGE_WITH________________________________JOB_NAME", "defaultJob")
                .build();
        AmazonS3Client mockClient = mockClient();
        doReturn(mockClient).when(fetchExecutor).s3Client(any(GoEnvironment.class));
        S3ArtifactStore mockStore = mockStore();

        doReturn(mockStore).when(fetchExecutor).getS3ArtifactStore(any(GoEnvironment.class), any(String.class));
        config = new Config(Maps.builder()
                .with(Constants.REPO, Maps.builder().with("value", "repo-with`~!@#$%^&*()-+=[{]}\\|;:'\",<.>/?").build())
                .with(Constants.PACKAGE, Maps.builder().with("value", "package-with`~!@#$%^&*()-+=[{]}\\|;:'\",<.>/?").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables));

        assertTrue(result.isSuccessful());
        assertThat(result.message(), is("Fetched all artifacts"));
        verify(mockStore, times(1)).getPrefix("TestPublish/defaultStage/defaultJob/20.1", "here/artifacts");
    }

    private Context mockContext(final Map<String, String> environmentMap) {
        Map<String, Object> contextMap = Maps.<String, Object>builder()
                .with("environmentVariables", environmentMap)
                .with("workingDirectory", "here")
                .build();
        return new MockContext(contextMap);
    }

    private S3ArtifactStore mockStore() { return mock(S3ArtifactStore.class); }

    private AmazonS3Client mockClient() { return mock(AmazonS3Client.class); }
}