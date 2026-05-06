package com.webhooks.sync;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhooks.config.AppProperties;
import com.webhooks.domain.JobStatus;
import com.webhooks.domain.SyncJob;
import com.webhooks.repository.SyncJobRepository;

@Service
public class SyncJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncJobExecutor.class);

    private final AppProperties appProperties;
    private final SyncJobRepository syncJobRepository;
    private final ProductSyncService productSyncService;

    public SyncJobExecutor(
        AppProperties appProperties,
        SyncJobRepository syncJobRepository,
        ProductSyncService productSyncService
    ) {
        this.appProperties = appProperties;
        this.syncJobRepository = syncJobRepository;
        this.productSyncService = productSyncService;
    }

    @Transactional
    public void recoverStuck() {
        Instant cutoff = Instant.now().minus(
            appProperties.getSync().getStuckProcessingRecoverAfterSeconds(),
            ChronoUnit.SECONDS
        );
        int n = syncJobRepository.recoverStuckProcessing(
            JobStatus.PENDING,
            JobStatus.PROCESSING,
            cutoff,
            Instant.now()
        );
        if (n > 0) {
            log.warn("Recovered {} jobs stuck in PROCESSING (older than {})", n, cutoff);
        }
    }

    @Transactional
    public void processJob(Long jobId) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.FAILED) {
            return;
        }
        if (job.getNextRetryAt().isAfter(Instant.now())) {
            return;
        }

        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(Instant.now());
        syncJobRepository.save(job);

        try {
            productSyncService.perform(job);
            job.setStatus(JobStatus.COMPLETED);
            job.setLastError(null);
        } catch (Exception e) {
            int nextAttempt = job.getAttempts() + 1;
            job.setAttempts(nextAttempt);
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.getClass().getSimpleName();
            }
            job.setLastError(truncate(msg, 8000));
            int max = appProperties.getSync().getMaxAttempts();
            if (nextAttempt >= max) {
                job.setStatus(JobStatus.DEAD);
                log.error("Job {} dead after {} attempts: {}", job.getId(), nextAttempt, msg, e);
            } else {
                job.setStatus(JobStatus.FAILED);
                long delaySec = backoffSeconds(nextAttempt);
                job.setNextRetryAt(Instant.now().plus(delaySec, ChronoUnit.SECONDS));
                log.warn("Job {} failed attempt {}/{}, retry in {}s: {}", job.getId(), nextAttempt, max, delaySec, msg);
            }
        }
        job.setUpdatedAt(Instant.now());
        syncJobRepository.save(job);
    }

    private long backoffSeconds(int attemptNumberOneBased) {
        long base = appProperties.getSync().getBaseBackoffSeconds();
        long exp = 1L << Math.min(attemptNumberOneBased - 1, 16);
        long sec = base * exp;
        return Math.min(sec, 86400L);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
