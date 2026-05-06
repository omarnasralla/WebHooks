package com.webhooks.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.webhooks.domain.JobStatus;
import com.webhooks.domain.SyncJob;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<SyncJob> findFirstByStatusInAndNextRetryAtBeforeOrderByCreatedAtAsc(
        List<JobStatus> statuses,
        Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SyncJob j SET j.status = :pending, j.updatedAt = :now
        WHERE j.status = :processing AND j.updatedAt < :cutoff
        """)
    int recoverStuckProcessing(
        @Param("pending") JobStatus pending,
        @Param("processing") JobStatus processing,
        @Param("cutoff") Instant cutoff,
        @Param("now") Instant now
    );
}
