package com.yun.aiinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.handler.BusinessException;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedissonClient redissonClient;

    /**
     * 执行带锁的操作
     */
    public <T> T executeWithLock(String lockKey, long waitTime,
                                 TimeUnit unit, LockedOperation<T> operation) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime,  unit)) {
                try {
                    return operation.execute();
                } finally {
                    lock.unlock();
                }
            }
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "获取锁失败: " + lockKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "获取锁被中断: " + lockKey);
        }
    }

    @FunctionalInterface
    public interface LockedOperation<T> {
        T execute();
    }
    /**
     * Stream 消息处理器接口
     */
    @FunctionalInterface
    public interface StreamMessageProcessor {
        void process(StreamMessageId messageId, Map<String, String> data);
    }

    /**
     * 消费 Stream 消息（优先回收超时 Pending，再阻塞读取新消息）
     *
     * @param streamKey              Stream 键
     * @param groupName              消费者组名
     * @param consumerName           消费者名
     * @param count                  每次读取新消息数量
     * @param blockTimeoutMs         阻塞等待超时时间（毫秒），0 表示无限等待
     * @param pendingIdleTimeoutMs   Pending 消息超过该 idle 时间后可被当前消费者回收
     * @param pendingClaimBatchSize  每轮最多回收的 Pending 消息数
     * @param processor              消息处理器
     * @return true 如果处理了消息，false 如果超时无消息
     */
    public boolean streamConsumeMessages(
            String streamKey,
            String groupName,
            String consumerName,
            int count,
            long blockTimeoutMs,
            long pendingIdleTimeoutMs,
            int pendingClaimBatchSize,
            StreamMessageProcessor processor){
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        Map<StreamMessageId, Map<String, String>> messages = reclaimPendingMessages(
                stream,
                streamKey,
                groupName,
                consumerName,
                pendingClaimBatchSize,
                pendingIdleTimeoutMs
        );
        if (processMessages(messages, processor)) {
            return true;
        }

        // 使用阻塞读取，让 Redis 服务端等待消息
        try{
            messages = stream.readGroup(groupName, consumerName, StreamReadGroupArgs.neverDelivered()
                    .count(count)
                    .timeout(Duration.ofMillis(blockTimeoutMs)));
        } catch (ClassCastException e) {
            // Redisson 4.0.0 bug: 无消息时返回 EmptyList 而非空 Map，内部强转失败。
            // 等价于"本次无消息"，静默返回即可。
            log.debug("Redisson 4.0.0 内部类型转换异常（空结果时触发），等价于本批无消息: stream={}, group={}",
                    streamKey, groupName);
            return false;
        }
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return processMessages(messages,processor);

    }

    private Map<StreamMessageId, Map<String,String>> reclaimPendingMessages(
            RStream<String, String> stream,
            String streamKey,
            String groupName,
            String consumerName,
            int count,
            long pendingIdleTimeoutMs
    ){
        if (pendingIdleTimeoutMs <= 0 || count <= 0) {
            return Map.of();
        }
        if (stream.size() == 0) {
            return Map.of();
        }
        AutoClaimResult<String, String> result;
        try{
            result = stream.autoClaim(
                    groupName,
                    consumerName,
                    pendingIdleTimeoutMs,
                    TimeUnit.MILLISECONDS,
                    StreamMessageId.MIN,
                    count
            );
        } catch (ClassCastException e) {
            // Redisson 4.0.0 空结果可能触发内部类型转换异常，等价于本轮无可回收消息。
            log.debug("Redisson 4.0.0 内部类型转换异常（无可回收消息）: stream={}, group={}",
                    streamKey, groupName);
            return Map.of();
        }
        //寻找幽灵消息并确认(PEL（待办清单）里还挂着号，但 Stream（仓库）里实体已经没了)
        List<StreamMessageId> deletedIds = result.getDeletedIds();
        if (deletedIds != null && !deletedIds.isEmpty()) {
            stream.ack(groupName, deletedIds.toArray(new StreamMessageId[0]));
        }

        Map<StreamMessageId, Map<String, String>> messages = result.getMessages();
        if (messages != null && !messages.isEmpty()) {
            log.info("Reclaimed Redis Stream pending messages: stream={}, group={}, consumer={}, count={}",
                    streamKey, groupName, consumerName, messages.size());
        }
        return messages == null ? Map.of() : messages;
    }

    private boolean processMessages(Map<StreamMessageId, Map<String, String>> messages,
                                   StreamMessageProcessor processor){
        if(messages==null||messages.isEmpty()){
            return false;
        }
        for(Map.Entry<StreamMessageId, Map<String,String>> entry:messages.entrySet()){
            processor.process(entry.getKey(), entry.getValue());
        }
        return true;
    }
    //发送消息到stream
    public String streamAdd(String streamKey, Map<String,String> messages, int maxLen){
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        StreamAddArgs<String, String> args = StreamAddArgs.entries(messages);
        if(maxLen>0){
            args.trimNonStrict().maxLen(maxLen);
        }
        StreamMessageId messageId = stream.add(args);
        log.debug("发送 Stream 消息: stream={}, messageId={}, maxLen={}", streamKey, messageId, maxLen);
        return messageId.toString();
    }
    //创建消费组
    public void createConsumeGroup(String streamKey, String groupName){
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        try{
            stream.createGroup(StreamCreateGroupArgs.name(groupName).makeStream());
            log.info("创建 Stream 消费者组: stream={}, group={}", streamKey, groupName);
        } catch (Exception e) {
            //组已存在，忽略
            if (e instanceof org.redisson.client.RedisException
                    && e.getMessage() != null
                    && e.getMessage().contains("BUSYGROUP")) {
                return;
            }
            log.warn("创建消费者组失败: stream={}, group={}, error={}", streamKey, groupName, e.getMessage());
        }

    }

    public void streamAck(String streamKey, String groupName, StreamMessageId... ids) {
        if (ids == null || ids.length == 0) {
            log.error("ACK 失败：传入的 ids 为空！");
            return;
        }
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        stream.ack(groupName, ids);
        log.info("ACK成功: {}", (Object) ids);
    }

}