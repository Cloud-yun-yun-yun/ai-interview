package com.yun.aiinterview.pojo.dto;

import lombok.Builder;

@Builder
public record ConsumerConfig(
        String streamKey,
        String groupName,
        String consumerName,
        String threadName,
        int batchSize,
        long pollIntervalMs,
        long pendingIdleTimeoutMs,
        int pendingClaimBatchSize
) {}