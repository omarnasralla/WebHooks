package com.webhooks.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhooks.domain.JobStatus;
import com.webhooks.domain.SyncJob;
import com.webhooks.repository.SyncJobRepository;

@Service
public class WebhookIngestService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestService.class);

    private final SyncJobRepository syncJobRepository;

    public WebhookIngestService(SyncJobRepository syncJobRepository) {
        this.syncJobRepository = syncJobRepository;
    }

    @Transactional
    public IngestResult ingest(String topic, String webhookId, byte[] rawBody) {
        String key = idempotencyKey(topic, webhookId, rawBody);
        if (syncJobRepository.existsByIdempotencyKey(key)) {
            log.debug("Duplicate webhook ignored, key={}", key);
            return IngestResult.duplicate(key);
        }
        SyncJob job = new SyncJob();
        job.setIdempotencyKey(key);
        job.setTopic(topic == null ? "" : topic);
        job.setPayload(new String(rawBody, StandardCharsets.UTF_8));
        job.setStatus(JobStatus.PENDING);
        job.setAttempts(0);
        job.setNextRetryAt(Instant.now());
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        syncJobRepository.save(job);
        return IngestResult.queued(key, job.getId());
    }

    private static String idempotencyKey(String topic, String webhookId, byte[] rawBody) {
        if (webhookId != null && !webhookId.isBlank()) {
            return topic + ":" + webhookId;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawBody);
            return topic + ":" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return topic + ":" + rawBody.length;
        }
    }

    public record IngestResult(boolean duplicate, String idempotencyKey, Long jobId) {
        static IngestResult duplicate(String key) {
            return new IngestResult(true, key, null);
        }

        static IngestResult queued(String key, Long id) {
            return new IngestResult(false, key, id);
        }
    }
}
