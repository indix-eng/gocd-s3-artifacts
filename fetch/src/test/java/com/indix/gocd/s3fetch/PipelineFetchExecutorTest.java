package com.indix.gocd.s3fetch;

import com.indix.gocd.utils.Constants;
import com.indix.gocd.utils.Context;
import com.indix.gocd.utils.GoEnvironment;
import com.indix.gocd.utils.TaskExecutionResult;
import com.indix.gocd.utils.mocks.MockContext;
import com.indix.gocd.utils.store.S3ArtifactStore;
import com.indix.gocd.utils.utils.Maps;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.indix.gocd.utils.Constants.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class PipelineFetchExecutorTest {

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
                .with("GO_DEPENDENCY_LOCATOR_MYMATERIAL", "pipeline/1/stage/1");

        config = new Config(Maps.builder()
                .with(Constants.MATERIAL_TYPE, Maps.builder().with("value", "Pipeline").build())
                .with(Constants.MATERIAL, Maps.builder().with("value", "mymaterial").build())
                .with(Constants.JOB, Maps.builder().with("value", "job").build())
                .with(Constants.DESTINATION, Maps.builder().with("value", "artifacts").build())
                .build());

        fetchExecutor = spy(new PipelineFetchExecutor());
    }

    @Test
    public void shouldBeSuccessResultONSuccessfulFetch() {
        Map<String, String> mockVariables = mockEnvironmentVariables.build();
        S3ArtifactStore mockStore = mockStore();

        doReturn(mockStore).when(fetchExecutor).getS3ArtifactStore(any(GoEnvironment.class), eq(bucket));
        TaskExecutionResult result = fetchExecutor.execute(config, mockContext(mockVariables) );

        assertTrue(result.isSuccessful());
        assertThat(result.message(), is("Fetched all artifacts"));
        verify(mockStore, times(1)).getPrefix("pipeline/stage/job/1.1", "here/artifacts");
    }

    private Context mockContext(final Map<String, String> environmentMap) {
        Map<String, Object> contextMap = Maps.<String, Object>builder()
                .with("environmentVariables", environmentMap)
                .with("workingDirectory", "here")
                .build();
        return new MockContext(contextMap);
    }

    private S3ArtifactStore mockStore() { return mock(S3ArtifactStore.class); }

}
