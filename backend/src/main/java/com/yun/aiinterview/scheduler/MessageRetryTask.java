package com.yun.aiinterview.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRetryTask {
    private static final long QUEUED_STALE_MINUTES = 2;
    private static final long PROCESSING_STALE_MINUTES = 20;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final FileService fileService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryMessages() {
        LocalDateTime now = LocalDateTime.now();
        recoverKbVectorPendingTask(now.minusMinutes(QUEUED_STALE_MINUTES));
    }

    private void recoverKbVectorPendingTask(LocalDateTime threshold){
        List<KnowledgeBase> kbs = knowledgeBaseService
                .lambdaQuery()
                .eq(KnowledgeBase::getVectorStatus, AsyncTaskStatus.PENDING)
                .lt(KnowledgeBase::getVectorStatusUpdatedAt, threshold)
                .list();
        if (!kbs.isEmpty()) {
            for(KnowledgeBase kb : kbs){
                byte[] bytes = fileStorageService.downloadFile(kb.getStorageKey());
                String content = fileService.parseText(bytes, kb.getName());
                knowledgeBaseService.sendGenerateTask(kb.getId(),content);
                log.info("重启知识库{}的向量生成任务",kb.getId());
            }
        }
    }
}