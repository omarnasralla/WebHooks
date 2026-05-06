package com.webhooks.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.webhooks.domain.JobStatus;
import com.webhooks.domain.SyncJob;
import com.webhooks.repository.SyncJobRepository;

/**
 * Single-threaded polling (one in-flight job). Scale horizontally later with SKIP LOCKED or a broker.
 */
@Component
public class SyncWorker {

    private final SyncJobRepository syncJobRepository;
    private final SyncJobExecutor syncJobExecutor;

    public SyncWorker(SyncJobRepository syncJobRepository, SyncJobExecutor syncJobExecutor) {
        this.syncJobRepository = syncJobRepository;
        this.syncJobExecutor = syncJobExecutor;
    }

    @Scheduled(fixedDelayString = "${webhook.sync.poll-interval-ms:2000}")
    public void poll() {
        syncJobExecutor.recoverStuck();
        Optional<SyncJob> next = syncJobRepository
            .findFirstByStatusInAndNextRetryAtBeforeOrderByCreatedAtAsc(
                List.of(JobStatus.PENDING, JobStatus.FAILED),
                Instant.now()
            );
        next.ifPresent(j -> syncJobExecutor.processJob(j.getId()));
    }
}
