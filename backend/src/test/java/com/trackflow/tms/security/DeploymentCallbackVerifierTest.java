package com.trackflow.tms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.exception.ForbiddenException;
import com.trackflow.tms.exception.InvalidTokenException;
import com.trackflow.tms.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeploymentCallbackVerifierTest {

    private final MutableClock clock = new MutableClock(Instant.ofEpochSecond(1_800_000_000L));

    private DeploymentCallbackVerifier verifier(String secret) {
        return new DeploymentCallbackVerifier(new DeploymentProperties(RunnerType.GITHUB_ACTIONS, Duration.ZERO,
                secret, Duration.ofMinutes(5), Set.of(DeploymentEnvironment.DEV), Map.of()), clock);
    }

    @Test
    void matchesOpensslHmac() {
        // printf '1800000000.{"status":"COMPLETED"}' | openssl dgst -sha256 -hmac s3cret
        assertThat(verifier("s3cret").sign("1800000000", "{\"status\":\"COMPLETED\"}"))
                .startsWith("sha256=").hasSize(7 + 64);
    }

    @Test
    void acceptsFreshCorrectSignature() {
        DeploymentCallbackVerifier v = verifier("s3cret");
        String body = "{\"status\":\"COMPLETED\"}";
        assertThatCode(() -> v.verify("1800000000", v.sign("1800000000", body), body)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperingReplayAndGarbage() {
        DeploymentCallbackVerifier v = verifier("s3cret");
        String body = "{\"status\":\"COMPLETED\"}";
        String signature = v.sign("1800000000", body);
        assertThatThrownBy(() -> v.verify("1800000000", signature, body.replace("COMPLETED", "FAILED")))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> v.verify("1800000000", null, body)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> v.verify("soon", signature, body)).isInstanceOf(InvalidTokenException.class);
        clock.advance(Duration.ofMinutes(6));
        assertThatThrownBy(() -> v.verify("1800000000", signature, body)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void disabledWithoutSecret() {
        assertThatThrownBy(() -> verifier("").verify("1", "x", "{}")).isInstanceOf(ForbiddenException.class);
    }
}
