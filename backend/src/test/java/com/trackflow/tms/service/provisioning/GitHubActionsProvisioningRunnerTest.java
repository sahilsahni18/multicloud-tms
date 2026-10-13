package com.trackflow.tms.service.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.service.DeploymentService;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubActionsProvisioningRunnerTest {

    private static final String DISPATCH_URL =
            "https://api.github.com/repos/acme/tms/actions/workflows/provision.yml/dispatches";

    private final DeploymentService deployments = mock(DeploymentService.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GitHubActionsProvisioningRunner runner(String token, String callbackUrl) {
        DeploymentProperties.GitHub github = new DeploymentProperties.GitHub(token, "acme/tms", "provision.yml",
                "main", callbackUrl, "https://api.github.com");
        DeploymentProperties properties = new DeploymentProperties(RunnerType.GITHUB_ACTIONS, Duration.ZERO,
                "secret", Duration.ofMinutes(5), Set.of(DeploymentEnvironment.DEV), Map.of(), github);
        return new GitHubActionsProvisioningRunner(deployments, properties, builder);
    }

    private static DeploymentRun run(DeploymentAction action) {
        return new DeploymentRun(42L, CloudProvider.AWS, "us-west-2", 1, DeploymentEnvironment.QA, action,
                RunnerType.GITHUB_ACTIONS);
    }

    @Test
    void dispatchesTheWorkflowWithTheDeploymentAsInputs() {
        server.expect(requestTo(DISPATCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ghp_test"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"ref":"main","inputs":{"deployment_id":"42","action":"plan","cloud":"aws",
                         "region":"us-west-2","environment":"qa","cluster_count":"1",
                         "callback_url":"http://tm.example"}}"""))
                .andRespond(withNoContent());

        runner("ghp_test", "http://tm.example/").dispatch(run(DeploymentAction.PLAN));

        server.verify();
        verifyNoInteractions(deployments);
    }

    @Test
    void rejectedDispatchFailsTheDeployment() {
        server.expect(requestTo(DISPATCH_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"Resource not accessible\"}"));

        runner("ghp_test", "http://tm.example").dispatch(run(DeploymentAction.APPLY));

        verify(deployments).applyEvent(eq(42L), eq(DeploymentStatus.FAILED), contains("HTTP 403"), any(), any());
    }

    @Test
    void missingTokenFailsWithoutCallingGitHub() {
        runner("", "http://tm.example").dispatch(run(DeploymentAction.APPLY));

        server.verify();
        verify(deployments).applyEvent(eq(42L), eq(DeploymentStatus.FAILED), contains("DEPLOY_GITHUB_TOKEN"),
                any(), any());
    }

    @Test
    void destroyIsPassedAsAnAction() {
        assertThat(GitHubActionsProvisioningRunner.inputs(run(DeploymentAction.DESTROY), "http://x"))
                .containsEntry("action", "destroy");
    }
}
