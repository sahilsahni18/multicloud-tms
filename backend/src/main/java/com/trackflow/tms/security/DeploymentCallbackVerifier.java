package com.trackflow.tms.security;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.exception.ForbiddenException;
import com.trackflow.tms.exception.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authenticates step reports from the provisioning pipeline. The workflow
 * signs {@code <unix-seconds>.<raw body>} with HMAC-SHA256 and sends
 * {@code X-TrackFlow-Timestamp} and {@code X-TrackFlow-Signature: sha256=<hex>}.
 * Old timestamps are rejected to stop replays.
 */
@Component
public class DeploymentCallbackVerifier {

    public static final String TIMESTAMP_HEADER = "X-TrackFlow-Timestamp";
    public static final String SIGNATURE_HEADER = "X-TrackFlow-Signature";
    private static final String PREFIX = "sha256=";

    private final String secret;
    private final Duration maxSkew;
    private final Clock clock;

    public DeploymentCallbackVerifier(DeploymentProperties properties, Clock clock) {
        this.secret = properties.callbackSecret();
        this.maxSkew = properties.callbackMaxSkew();
        this.clock = clock;
    }

    public void verify(String timestamp, String signature, String body) {
        if (!StringUtils.hasText(secret)) {
            throw new ForbiddenException("Deployment callbacks are disabled: no callback secret is configured");
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("Callback timestamp is invalid");
        }
        if (Math.abs(clock.instant().getEpochSecond() - sentAt) > maxSkew.toSeconds()) {
            throw new InvalidTokenException("Callback timestamp is outside the allowed window");
        }
        byte[] expected = sign(timestamp, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTokenException("Callback signature is invalid");
        }
    }

    /** {@code sha256=<hex HMAC of "timestamp.body">}; the workflow computes the same with openssl. */
    public String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
