package com.yun.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yun.aiinterview.entity.InterviewSession;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.InterviewQuestionMapper;
import com.yun.aiinterview.mapper.InterviewSessionMapper;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import lombok.RequiredArgsConstructor;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.entity.KnowledgeBaseQuestion;
import com.yun.aiinterview.mapper.KnowledgeBaseMapper;
import com.yun.aiinterview.mapper.KnowledgeBaseQuestionMapper;
import com.yun.aiinterview.pojo.dto.QuestionDTO;
import com.yun.aiinterview.pojo.vo.QuestionGenStatus;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.StreamMessageId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncStateService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseQuestionMapper knowledgeBaseQuestionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;

    /**
     * 问题生成任务的状态处理
     */
    @Transactional
    public boolean tryMarkProcessing(Long kbId, String taskId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE"); // 核心：拼上排他锁
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null||!kb.getQuestionGenTaskId().equals(taskId)||kb.getQuestionGenStatus() == QuestionGenStatus.COMPLETED) {
            return false;
        }
        kb.setQuestionGenStatus(QuestionGenStatus.PROCESSING);
        kb.setQuestionGenUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        return true;
    }

    @Transactional
    public boolean markCompleted(List<QuestionDTO> result, Long kbId, String taskId) {
        List<KnowledgeBaseQuestion> questions = result.stream()
                .map(q -> KnowledgeBaseQuestion.builder()
                        .type(q.type())
                        .question(q.question())
                        .topicSummary(q.topicSummary())
                        .referenceAnswer(q.referenceAnswer())
                        .scoringRubric(q.scoringRubric())
                        .kbId(kbId)
                        .build()
                ).collect(Collectors.toList());
        knowledgeBaseQuestionMapper.saveList(questions);
        log.info("题目已入库");
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null||kb.getQuestionGenStatus() != QuestionGenStatus.PROCESSING) {
            return false;
        }
        kb.setQuestionGenStatus(QuestionGenStatus.COMPLETED);
        kb.setQuestionGenUpdatedAt(LocalDateTime.now());
        kb.setQuestionGenTaskId(taskId);
        knowledgeBaseMapper.updateById(kb);
        log.info("题目生成任务完成");
        return true;
    }

    @Transactional
    public boolean markFailed(Long kbId, String taskId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if (kb.getQuestionGenStatus() == QuestionGenStatus.COMPLETED) {
            return false;
        }
        kb.setQuestionGenStatus(QuestionGenStatus.FAILED);
        kb.setQuestionGenUpdatedAt(LocalDateTime.now());
        kb.setQuestionGenTaskId(taskId);
        knowledgeBaseMapper.updateById(kb);
        return true;
    }

    @Transactional
    public boolean reMarkQueued(Long kbId, String taskId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null||kb.getQuestionGenStatus() != QuestionGenStatus.PROCESSING) {
            return false;
        }
        kb.setQuestionGenStatus(QuestionGenStatus.QUEUED);
        kb.setQuestionGenTaskId(taskId);
        kb.setQuestionGenUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        return true;
    }

    /**
     * 知识库向量化任务的状态处理
     */
    @Transactional
    public boolean vectorMarkProcessing(Long kbId){
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null||kb.getVectorStatus() == AsyncTaskStatus.COMPLETED) {
            return false;
        }
        kb.setVectorStatus(AsyncTaskStatus.PROCESSING);
        kb.setVectorStatusUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        return true;
    }

    @Transactional
    public boolean vectorMarkCompleted(Long kbId, Integer totalChunks){
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null||kb.getVectorStatus() != AsyncTaskStatus.PROCESSING) {
            return false;
        }
        kb.setVectorStatus(AsyncTaskStatus.COMPLETED);
        kb.setVectorStatusUpdatedAt(LocalDateTime.now());
        kb.setChunkCount(totalChunks);
        knowledgeBaseMapper.updateById(kb);
        return true;
    }

    @Transactional
    public boolean vectorMarkFailed(Long kbId){
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getId, kbId)
                .last("FOR UPDATE");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if(kb == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在: kbId=" + kbId);
        }
        if(kb.getVectorStatus() == AsyncTaskStatus.COMPLETED){
            log.info("知识库向量化务已被成功处理，无需变更为失败 ");
            return false;
        }
        kb.setVectorStatus(AsyncTaskStatus.FAILED);
        kb.setVectorStatusUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        return true;
    }
}
