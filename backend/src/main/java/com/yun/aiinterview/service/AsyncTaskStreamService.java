package com.yun.aiinterview.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.constant.AsyncTaskConstants;
import com.yun.aiinterview.pojo.dto.ConsumerConfig;
import org.redisson.api.StreamMessageId;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncTaskStreamService {

    private final RedisService redisService;

    private final Map<String, ExecutorService> consumerExecutors = new ConcurrentHashMap<>();
    private final Map<String, String> consumerNames = new ConcurrentHashMap<>();
    private final Map<String, ConsumerConfig> consumerConfigs = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    public String sendTask(String streamKey, Map<String, String> messages) {
        return sendTask(streamKey, messages, AsyncTaskConstants.STREAM_MAX_LEN);
    }

    public String sendTask(String streamKey, Map<String, String> messages, int maxLen) {
        return redisService.streamAdd(streamKey, messages, maxLen);
    }

    public void registerConsumer(ConsumerConfig config, RedisService.StreamMessageProcessor processor) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, config.threadName());
            t.setDaemon(true);
            return t;
        });

        String consumerName = config.consumerName();

        redisService.createConsumeGroup(config.streamKey(), config.groupName());

        AtomicBoolean running = new AtomicBoolean(true);
        executor.submit(() -> startConsumerLoop(config, consumerName, processor, running));

        consumerExecutors.put(config.streamKey(), executor);
        consumerNames.put(config.streamKey(), consumerName);
        consumerConfigs.put(config.streamKey(), config);
        runningFlags.put(config.streamKey(), running);
    }

    private void startConsumerLoop(ConsumerConfig config, String consumerName,
                                   RedisService.StreamMessageProcessor processor, AtomicBoolean running) {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                        config.streamKey(),
                        config.groupName(),
                        consumerName,
                        config.batchSize(),
                        config.pollIntervalMs(),
                        config.pendingIdleTimeoutMs(),
                        config.pendingClaimBatchSize(),
                        processor);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Consumer thread interrupted: stream={}", config.streamKey());
                    break;
                }
                log.error("Consumer error, will retry: stream={}", config.streamKey(), e);
            }
        }
        log.info("Consumer stopped: stream={}", config.streamKey());
    }

    public void ackMessage(String streamKey, String groupName, StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey, groupName, messageId);
        } catch (Exception e) {
            log.error("Failed to ack stream message: streamKey={}, groupName={}, messageId={}", streamKey, groupName, messageId, e);
        }
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all async task consumers...");
        runningFlags.forEach((streamKey, running) -> running.set(false));
        consumerExecutors.forEach((streamKey, executor) -> {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
                log.info("Consumer executor shutdown: stream={}", streamKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
        runningFlags.clear();
        consumerExecutors.clear();
        consumerNames.clear();
        consumerConfigs.clear();
    }
}