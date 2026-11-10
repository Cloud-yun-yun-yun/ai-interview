package com.yun.aiinterview.scheduler;

import com.yun.aiinterview.entity.InterviewSession;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.pojo.vo.QuestionGenStatus;
import com.yun.aiinterview.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRetryTask {
    private static final long QUEUED_STALE_MINUTES = 2;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final FileService fileService;
    private final KbQuestionGenService kbQuestionGenService;
    private final InterviewService interviewService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryMessages() {
        LocalDateTime now = LocalDateTime.now();
        recoverKbVectorTask(now.minusMinutes(QUEUED_STALE_MINUTES));
        recoverKbQuestionGenTask(now.minusMinutes(QUEUED_STALE_MINUTES));
        recoverInterviewGenTask(now.minusMinutes(QUEUED_STALE_MINUTES));
        recoverInterviewEvalTask(now.minusMinutes(QUEUED_STALE_MINUTES));
    }

    private void recoverKbVectorTask(LocalDateTime threshold){
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
    private void recoverKbQuestionGenTask(LocalDateTime threshold){
        List<KnowledgeBase> kbs = knowledgeBaseService
                .lambdaQuery()
                .eq(KnowledgeBase::getQuestionGenStatus, QuestionGenStatus.QUEUED)
                .lt(KnowledgeBase::getQuestionGenUpdatedAt, threshold)
                .list();
        if (!kbs.isEmpty()) {
            for(KnowledgeBase kb : kbs){
                kbQuestionGenService.sendGenerateTask(kb.getId(),kb.getQuestionGenTaskId());
                log.info("重启知识库{}的问题生成任务",kb.getId());
            }
        }
    }
    private void recoverInterviewGenTask(LocalDateTime threshold){
        List<InterviewSession> sessions = interviewService
                .lambdaQuery()
                .eq(InterviewSession::getSessionQuestionGenStatus, AsyncTaskStatus.PENDING)
                .lt(InterviewSession::getAsyncTaskUpdatedAt, threshold)
                .list();
        if (!sessions.isEmpty()) {
            for(InterviewSession session : sessions){
                // 仅简历面试才有出题异步任务；知识库面试创建时题目直接落库为 COMPLETED，此处为兜底防御
                if (session.getResumeId() == null) {
                    continue;
                }
                interviewService.sendQuestionGenTask(session.getSessionId(), session.getUserId(), session.getResumeId());
                log.info("重启面试{}的题目生成任务", session.getSessionId());
            }
        }
    }
    private void recoverInterviewEvalTask(LocalDateTime threshold){
        List<InterviewSession> sessions = interviewService
                .lambdaQuery()
                .eq(InterviewSession::getSessionEvaluateStatus, AsyncTaskStatus.PENDING)
                .lt(InterviewSession::getAsyncTaskUpdatedAt, threshold)
                .list();
        if (!sessions.isEmpty()) {
            for(InterviewSession session : sessions){
                interviewService.sendGenerateTask(session.getSessionId(), session.getUserId());
                log.info("重启面试{}的评估任务", session.getSessionId());
            }
        }
    }
}