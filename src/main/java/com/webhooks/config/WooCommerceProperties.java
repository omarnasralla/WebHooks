package com.webhooks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>Site A (read) is optional but strongly recommended for variable products — webhooks often
 * omit full variation bodies until we fetch them from the REST API.</p>
 */
@ConfigurationProperties(prefix = "woocommerce")
public class WooCommerceProperties {

    private String targetBaseUrl;

    private String targetConsumerKey;
    private String targetConsumerSecret;

    private String sourceBaseUrl;
    private String sourceConsumerKey;
    private String sourceConsumerSecret;
    private boolean sourceReadEnabled = true;

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public String getTargetConsumerKey() {
        return targetConsumerKey;
    }

    public void setTargetConsumerKey(String targetConsumerKey) {
        this.targetConsumerKey = targetConsumerKey;
    }

    public String getTargetConsumerSecret() {
        return targetConsumerSecret;
    }

    public void setTargetConsumerSecret(String targetConsumerSecret) {
        this.targetConsumerSecret = targetConsumerSecret;
    }

    public String getSourceBaseUrl() {
        return sourceBaseUrl;
    }

    public void setSourceBaseUrl(String sourceBaseUrl) {
        this.sourceBaseUrl = sourceBaseUrl;
    }

    public String getSourceConsumerKey() {
        return sourceConsumerKey;
    }

    public void setSourceConsumerKey(String sourceConsumerKey) {
        this.sourceConsumerKey = sourceConsumerKey;
    }

    public String getSourceConsumerSecret() {
        return sourceConsumerSecret;
    }

    public void setSourceConsumerSecret(String sourceConsumerSecret) {
        this.sourceConsumerSecret = sourceConsumerSecret;
    }

    public boolean isSourceReadEnabled() {
        return sourceReadEnabled;
    }

    public void setSourceReadEnabled(boolean sourceReadEnabled) {
        this.sourceReadEnabled = sourceReadEnabled;
    }

    public boolean hasSourceCredentials() {
        return sourceBaseUrl != null
            && !sourceBaseUrl.isBlank()
            && sourceConsumerKey != null && !sourceConsumerKey.isBlank()
            && sourceConsumerSecret != null && !sourceConsumerSecret.isBlank();
    }
}
