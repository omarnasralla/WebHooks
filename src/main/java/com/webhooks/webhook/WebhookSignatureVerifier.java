package com.webhooks.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.webhooks.config.AppProperties;

@Component
public class WebhookSignatureVerifier {

    private final AppProperties props;

    public WebhookSignatureVerifier(AppProperties props) {
        this.props = props;
    }

    /**
     * WooCommerce sends Base64(HMAC-SHA256(raw body, webhook secret)).
     */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                props.getSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(key);
            byte[] hmac = mac.doFinal(rawBody);
            String trimmed = signatureHeader.trim();
            try {
                byte[] received = Base64.getDecoder().decode(trimmed);
                return java.security.MessageDigest.isEqual(hmac, received);
            } catch (IllegalArgumentException ex) {
                byte[] expected = Base64.getEncoder().encodeToString(hmac).getBytes(StandardCharsets.UTF_8);
                byte[] actual = trimmed.getBytes(StandardCharsets.UTF_8);
                if (expected.length != actual.length) {
                    return false;
                }
                int diff = 0;
                for (int i = 0; i < expected.length; i++) {
                    diff |= expected[i] ^ actual[i];
                }
                return diff == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
