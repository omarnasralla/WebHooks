package com.webhooks.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhooks.config.AppProperties;
import com.webhooks.webhook.WebhookIngestService;
import com.webhooks.webhook.WebhookSignatureVerifier;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/webhooks")
public class WooCommerceWebhookController {

    private final AppProperties appProperties;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookIngestService ingestService;

    public WooCommerceWebhookController(
        AppProperties appProperties,
        WebhookSignatureVerifier signatureVerifier,
        WebhookIngestService ingestService
    ) {
        this.appProperties = appProperties;
        this.signatureVerifier = signatureVerifier;
        this.ingestService = ingestService;
    }

    /**
     * Point WooCommerce (Site A) webhooks here.
     * Use the same secret as configured in {@code webhook.secret} / {@code WEBHOOK_SECRET}.
     */
    @PostMapping(value = "/woocommerce", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
        @RequestBody byte[] body,
        @RequestHeader(value = "X-WC-Webhook-Topic", required = false) String topic,
        @RequestHeader(value = "X-WC-Webhook-ID", required = false) String webhookId,
        HttpServletRequest request
    ) {
        String signature = request.getHeader(appProperties.getSignatureHeader());
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!signatureVerifier.isValid(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String t = topic == null ? "" : topic;
        ingestService.ingest(t, webhookId, body);
        return ResponseEntity.accepted().build();
    }
}
