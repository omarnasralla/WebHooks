package com.webhooks.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.webhooks.config.AppProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    @Test
    void acceptsWooCommerceStyleHmacBase64() throws Exception {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        String secret = "test-secret";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String good = Base64.getEncoder().encodeToString(mac.doFinal(body));

        AppProperties props = new AppProperties();
        props.setSecret(secret);
        WebhookSignatureVerifier v = new WebhookSignatureVerifier(props);
        assertTrue(v.isValid(body, good));
        assertFalse(v.isValid(body, "bad"));
    }
}
