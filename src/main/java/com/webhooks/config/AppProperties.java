package com.webhooks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "webhook")
public class AppProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String signatureHeader = "X-WC-Webhook-Signature";

    @NotNull
    private Sync sync = new Sync();

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public static class Sync {

        @NotBlank
        private String pollIntervalMs = "2000";

        @Positive
        private int maxAttempts = 7;

        @Positive
        private long baseBackoffSeconds = 30;

        private long stuckProcessingRecoverAfterSeconds = 600;

        public String getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(String pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBaseBackoffSeconds() {
            return baseBackoffSeconds;
        }

        public void setBaseBackoffSeconds(long baseBackoffSeconds) {
            this.baseBackoffSeconds = baseBackoffSeconds;
        }

        public long getStuckProcessingRecoverAfterSeconds() {
            return stuckProcessingRecoverAfterSeconds;
        }

        public void setStuckProcessingRecoverAfterSeconds(long stuckProcessingRecoverAfterSeconds) {
            this.stuckProcessingRecoverAfterSeconds = stuckProcessingRecoverAfterSeconds;
        }
    }
}
