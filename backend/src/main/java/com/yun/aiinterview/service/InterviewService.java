package com.yun.aiinterview.service;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.config.ResourcesProperties;
import com.yun.aiinterview.constant.AsyncTaskConstants;
import com.yun.aiinterview.entity.*;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.InterviewQuestionMapper;
import com.yun.aiinterview.mapper.InterviewSessionMapper;
import com.yun.aiinterview.mapper.KnowledgeBaseMapper;
import com.yun.aiinterview.mapper.KnowledgeBaseQuestionMapper;
import com.yun.aiinterview.pojo.dto.CreateInterviewSessionDTO;
import com.yun.aiinterview.pojo.dto.CreateKbInterviewSessionDTO;
import com.yun.aiinterview.pojo.dto.QuestionAnswer;
import com.yun.aiinterview.pojo.dto.ConsumerConfig;
import com.yun.aiinterview.pojo.vo.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.redisson.api.StreamMessageId;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yun.aiinterview.constant.AsyncTaskConstants.BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_SESSION_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_USER_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.INTERVIEW_EVALUATION_CONSUMER_PREFIX;
import static com.yun.aiinterview.constant.AsyncTaskConstants.INTERVIEW_EVALUATION_GROUP_NAME;
import static com.yun.aiinterview.constant.AsyncTaskConstants.INTERVIEW_EVALUATION_STREAM_KEY;
import static com.yun.aiinterview.constant.AsyncTaskConstants.MAX_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_CLAIM_BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_IDLE_TIMEOUT_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.POLL_INTERVAL_MS;


@Service
@Slf4j
public class InterviewService extends ServiceImpl<InterviewSessionMapper, InterviewSession> {

    private final ResumeService resumeService;
    private final ChatClient generalChatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate evaluationSystemPromptTemplate;
    private final PromptTemplate evaluationUserPromptTemplate;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final RedisService redisService;
    private final KnowledgeBaseQuestionMapper knowledgeBaseQuestionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AsyncTaskStreamService asyncTaskService;

    private static final String threadName = "interview-evaluation-consumer";
    private String consumerName;

    private static final String CACHE_LOCK_PREFIX = "interview:session:lock:";
    public InterviewService(ResumeService resumeService,
                            ChatClient generalChatClient,
                            ResourceLoader resourceLoader,
                            ResourcesProperties properties,
                            InterviewQuestionMapper interviewQuestionMapper,
                            RedisService redisService,
                            KnowledgeBaseQuestionMapper knowledgeBaseQuestionMapper,
                            KnowledgeBaseMapper knowledgeBaseMapper,
                            AsyncTaskStreamService asyncTaskService
) throws IOException {
        this.resumeService = resumeService;
        this.generalChatClient = generalChatClient;
        this.systemPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getInterviewResumeSystem())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getInterviewResumeUser())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.evaluationSystemPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getInterviewEvaluationSystem())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.evaluationUserPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getInterviewEvaluationUser())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.redisService = redisService;
        this.knowledgeBaseQuestionMapper = knowledgeBaseQuestionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.asyncTaskService = asyncTaskService;
    }

    public InterviewSessionVO createInterview(CreateInterviewSessionDTO dto, Long userId) {
        return redisService.executeWithLock(
                CACHE_LOCK_PREFIX + dto.requestId(),
                10,
                TimeUnit.SECONDS,
                () -> createByResume(
                        dto.resumeId(),
                        userId
                )
        );
    }

    public InterviewSessionVO createInterview(CreateKbInterviewSessionDTO dto, Long userId) {
        return redisService.executeWithLock(
                CACHE_LOCK_PREFIX + dto.requestId(),
                10,
                TimeUnit.SECONDS,
                () -> createByKb(
                        userId,
                        dto.kbId()
                )
        );
    }

    @Transactional
    public InterviewSessionVO createByResume( Long resumeId, Long userId) {

        // 同一份简历只能存在一个未提交的会话：若已有 CREATED 状态的会话，禁止重复创建
        Long activeCount = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getResumeId, resumeId)
                .eq(InterviewSession::getStatus, InterviewSession.STATUS_CREATED)
                .count();
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "该简历已有进行中的面试,请先提交后再重新创建");
        }
        String resumeContent = fetchResumeContent(resumeId);
        if (resumeContent.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "简历不存在");
        }

        //纯AI根据简历出题6道面试题目
        List<InterviewQuestionVO> aiVos = aiPublish(resumeContent);
        if (aiVos.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI 出题失败，请稍后重试");
        }
        log.info("AI 根据简历生成 {} 道题目", aiVos.size());
        //生成题号（从 1 递增）
        List<InterviewQuestionVO> voList = IntStream.range(0, aiVos.size())
                .mapToObj(i -> new InterviewQuestionVO(
                        i + 1,
                        aiVos.get(i).questionContent(),
                        aiVos.get(i).topicSummary(),
                        aiVos.get(i).type(),
                        null
                ))
                .collect(Collectors.toList());

        //保存面试会话
        InterviewSession interviewSession = InterviewSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .currentIndex(1)
                .userId(userId)
                .resumeId(resumeId)
                .totalQuestions(voList.size())
                .status(InterviewSession.STATUS_CREATED)
                .build();
        save(interviewSession);
        log.info("保存面试会话，sessionId: {}", interviewSession.getSessionId());

        //保存面试题目
        List<InterviewQuestion> iqs = new ArrayList<>();
        for (InterviewQuestionVO vo : voList) {
            InterviewQuestion iq = InterviewQuestion
                    .builder()
                    .sessionId(interviewSession.getSessionId())
                    .questionIndex(vo.questionIndex())
                    .type(vo.type())
                    .questionContent(vo.questionContent())
                    .topicSummary(vo.topicSummary())
                    .build();
            iqs.add(iq);
        }
        interviewQuestionMapper.insertList(iqs);

        //返回面试会话VO
        InterviewSessionVO interviewSessionVO = new InterviewSessionVO(
                interviewSession.getSessionId(),
                userId,
                interviewSession.getCurrentIndex(),
                interviewSession.getStatus());
        return interviewSessionVO;
    }


    private List<InterviewQuestionVO> aiPublish(String resumeContent){
        log.info("开始构建提示词");
        String systemPrompt = systemPromptTemplate.render();
        HashMap<String, Object> variables = new HashMap<>();
        variables.put("resumeContent", resumeContent);
        String userPrompt = userPromptTemplate.render(variables);
        log.info("提示词构建完成");

        log.info("开始ai出题");

        List<InterviewQuestionVO> aiVos = Collections.emptyList();
        try{
            InterviewQuestionResponse response = generalChatClient
                    .prompt()
                    .user(userPrompt)
                    .system(systemPrompt)
                    .call()
                    .entity(InterviewQuestionResponse.class);// 指定 Record 类
            aiVos = response.questions() != null ? response.questions() : Collections.emptyList();
        }catch(Exception e){
            log.error("AI 出题失败", e);
        }
        return aiVos;
    }

    @Transactional
    public InterviewSessionVO createByKb(Long userId, Long kbId) {
        if (kbId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "kbId is null");
        }
        Long activeCount = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getKbId, kbId)
                .eq(InterviewSession::getStatus, InterviewSession.STATUS_CREATED)
                .count();
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "该知识库已有进行中的面试,请先提交后再重新创建");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if(kb == null){
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        if(!QuestionGenStatus.COMPLETED.equals(kb.getQuestionGenStatus())){
            throw new BusinessException(HttpStatus.CONFLICT, "知识库问题生成未完成");
        }
        List<KnowledgeBaseQuestion> questionList = knowledgeBaseQuestionMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseQuestion>()
                .eq(KnowledgeBaseQuestion::getKbId, kbId));
        if(questionList.isEmpty()){
            throw new BusinessException(HttpStatus.CONFLICT, "知识库问题生成为空");
        }

        List<InterviewQuestionVO> voList = IntStream.range(0, questionList.size())
                .mapToObj(i -> new InterviewQuestionVO(
                        i + 1,
                        questionList.get(i).getQuestion(),
                        questionList.get(i).getTopicSummary(),
                        questionList.get(i).getType(),
                        null
                ))
                .collect(Collectors.toList());
        // 保存面试会话
        InterviewSession interviewSession = InterviewSession
                .builder()
                .sessionId(UUID.randomUUID().toString())
                .currentIndex(1)
                .userId(userId)
                .resumeId(null)
                .totalQuestions(voList.size())
                .kbId(kbId)
                .status(InterviewSession.STATUS_CREATED)
                .build();
        save(interviewSession);
        log.info("保存面试会话，sessionId: {}", interviewSession.getSessionId());

        // 保存面试题目
        List<InterviewQuestion> iqs = new ArrayList<>();
        for (InterviewQuestionVO vo : voList) {
            InterviewQuestion iq = InterviewQuestion
                    .builder()
                    .sessionId(interviewSession.getSessionId())
                    .questionIndex(vo.questionIndex())
                    .type(vo.type())
                    .questionContent(vo.questionContent())
                    .topicSummary(vo.topicSummary())
                    .build();
            iqs.add(iq);
        }
        interviewQuestionMapper.insertList(iqs);
        InterviewSessionVO interviewSessionVO = new InterviewSessionVO(
                interviewSession.getSessionId(),
                userId,
                interviewSession.getCurrentIndex(),
                interviewSession.getStatus()
        );
        return interviewSessionVO;
    }

    @Transactional
    public void submit(QuestionAnswer qa, Long userId){
        InterviewSession interviewSession = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getSessionId, qa.sessionId())
                .one();
        if(interviewSession == null){
            throw new BusinessException(HttpStatus.NOT_FOUND, "面试会话不存在");
        }
        if (!InterviewSession.STATUS_CREATED.equals(interviewSession.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "不可重复提交");
        }
        if (!interviewSession.getCurrentIndex().equals(qa.currentIndex())) {
            throw new BusinessException(HttpStatus.CONFLICT, "提交的题目序号与当前进度不一致");
        }
        if (qa.answer() == null || qa.answer().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "回答内容不能为空");
        }

        boolean isLast = interviewSession.getTotalQuestions().equals(qa.currentIndex());

        // 1. 先持久化当前题目的答案，确保评估消费端读取到的数据是最新的
        LambdaUpdateWrapper<InterviewQuestion> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(InterviewQuestion::getSessionId, qa.sessionId())
                .eq(InterviewQuestion::getQuestionIndex, qa.currentIndex())
                .set(InterviewQuestion::getUserAnswer, qa.answer());

        int rows = interviewQuestionMapper.update(null, updateWrapper);
        if (rows == 0) {
            // 提示：更新了 0 行，说明没找到对应的 sessionId 和 index 记录
            log.warn("未找到对应的题目记录，更新失败。sessionId={}, index={}", qa.sessionId(), qa.currentIndex());
        }

        // 2. 推进会话状态 / 进度
        if (isLast) {
            interviewSession.setStatus(InterviewSession.STATUS_SUBMITTED);
            interviewSession.setAsyncTaskStatus(AsyncTaskStatus.PENDING);
        } else {
            interviewSession.setCurrentIndex(qa.currentIndex() + 1);
        }
        updateById(interviewSession);

        // 3. 末题提交后触发评估：在事务提交之后再入队，
        //    确保消费端能读到已提交的答案与 SUBMITTED 状态，避免竞态导致任务被幂等跳过
        if (isLast) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendGenerateTask(qa.sessionId(), userId);
                }
            });
        }
    }

    public void sendGenerateTask(String sessionId, Long userId) {
        sendGenerateTask(sessionId, userId, 0);
    }
    private void sendGenerateTask(String sessionId, Long userId, int retryCount) {
        Map<String, String> messages = Map.of(
                AsyncTaskConstants.FIELD_SESSION_ID, sessionId,
                AsyncTaskConstants.FIELD_USER_ID, userId.toString(),
                AsyncTaskConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
        );
        try {
            String messageId = asyncTaskService.sendTask(AsyncTaskConstants.INTERVIEW_EVALUATION_STREAM_KEY, messages);
            log.info("面试评估任务已发送到Stream, messageId={}", messageId);
        } catch (Exception e) {
            boolean updated = lambdaUpdate()
                    .eq(InterviewSession::getSessionId, sessionId)// 保证幂等
                    .set(InterviewSession::getAsyncTaskStatus, AsyncTaskStatus.FAILED) // 改为失败状态
                    .update();

            if (!updated) {
                log.warn("入队失败后更新状态失败，可能任务已被覆盖或删除, sessionId={}", sessionId);
            }
        }
    }

    @PostConstruct
    public void init() {
        this.consumerName = INTERVIEW_EVALUATION_CONSUMER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        asyncTaskService.registerConsumer(
                new ConsumerConfig(
                        INTERVIEW_EVALUATION_STREAM_KEY,
                        INTERVIEW_EVALUATION_GROUP_NAME,
                        consumerName,
                        threadName,
                        BATCH_SIZE,
                        POLL_INTERVAL_MS,
                        PENDING_IDLE_TIMEOUT_MS,
                        PENDING_CLAIM_BATCH_SIZE
                ),
                this::processMessage
        );
    }

    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(FIELD_SESSION_ID);
        String userIdStr = data.get(FIELD_USER_ID);
        String retryCountStr = data.get(FIELD_RETRY_COUNT);
        if (sessionId == null || userIdStr == null) {
            log.warn("面试评估消息格式错误，丢弃: messageId={}", messageId);
            asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
            return;
        }
        Long userId = Long.parseLong(userIdStr);
        try {
            InterviewSession session = baseMapper.selectById(sessionId);
            if (session == null) {
                log.warn("面试会话不存在，丢弃: messageId={}", messageId);
                asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
                return;
            }
            if (!userId.equals(session.getUserId())) {
                log.warn("面试评估消息归属不匹配，丢弃: messageId={}", messageId);
                asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
                return;
            }
            // 幂等：仅处理已提交未评估的会话
            if (!InterviewSession.STATUS_SUBMITTED.equals(session.getStatus())) {
                log.info("会话状态非 SUBMITTED，可能已评估，跳过: sessionId={}, status={}", sessionId, session.getStatus());
                asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
                return;
            }

            // 置为处理中
            updateAsyncTaskStatus(sessionId, AsyncTaskStatus.PROCESSING);

            // 分支执行：resumeId 不为空走简历面试，否则（kbId）走知识库面试
            EvaluationResponseVO response = doEvaluate(session);

            // 写回会话：状态改为已评估，记录总分与反馈
            InterviewSession update = InterviewSession.builder()
                    .sessionId(session.getSessionId())
                    .status(InterviewSession.STATUS_EVALUATED)
                    .totalScore(response.overallScore())
                    .feedback(response.feedback())
                    .asyncTaskStatus(AsyncTaskStatus.COMPLETED)
                    .build();
            baseMapper.updateById(update);

            // 确认消息
            asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
        } catch (Exception e) {
            try {
                log.error("面试评估失败，sessionId={}", sessionId, e);
                int retryCount;
                try {
                    retryCount = retryCountStr == null ? 0 : Integer.parseInt(retryCountStr);
                } catch (NumberFormatException nfe) {
                    log.warn("非法 retryCount，按已达上限处理: retryCountStr={}", retryCountStr, nfe);
                    retryCount = MAX_RETRY_COUNT;
                }
                if (retryCount >= MAX_RETRY_COUNT) {
                    updateAsyncTaskStatus(sessionId, AsyncTaskStatus.FAILED);
                } else {
                    sendGenerateTask(sessionId, userId, retryCount + 1);
                }
            } finally {
                // 无论如何都 ack，避免坏消息被 autoClaim 无限回收
                asyncTaskService.ackMessage(INTERVIEW_EVALUATION_STREAM_KEY, INTERVIEW_EVALUATION_GROUP_NAME, messageId);
            }
        }
    }

    private void updateAsyncTaskStatus(String sessionId, AsyncTaskStatus status) {
        InterviewSession update = InterviewSession.builder()
                .sessionId(sessionId)
                .asyncTaskStatus(status)
                .build();
        baseMapper.updateById(update);
    }

    private EvaluationResponseVO doEvaluate(InterviewSession session) {
        String resumeText = fetchResumeContent(session.getResumeId());
        String qaRecords = buildQaRecords(session);

        // 渲染 system / user 提示词
        String system = evaluationSystemPromptTemplate.getTemplate();
        Map<String, Object> variables = new HashMap<>();
        variables.put("resumeText", resumeText);
        variables.put("qaRecords", qaRecords);
        String user = evaluationUserPromptTemplate.render(variables);

        // 调用模型，解析为结构化评估结果
        EvaluationResponseVO response = generalChatClient.prompt()
                .system(system)
                .user(user)
                .call()
                .entity(EvaluationResponseVO.class);
        if (response == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 评估结果为空");
        }
        return sanitize(response);
    }

    /**
     * 组装问答记录。
     * 简历面试时仅包含题干与候选人回答。
     */
    private String buildQaRecords(InterviewSession session) {
        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, session.getSessionId())
                        .orderByAsc(InterviewQuestion::getQuestionIndex));

        StringBuilder sb = new StringBuilder();
        for (InterviewQuestion q : questions) {
            sb.append("第").append(q.getQuestionIndex()).append("题");
            if (q.getType() != null && !q.getType().isBlank()) {
                sb.append("（类型：").append(q.getType()).append("）");
            }
            sb.append("：\n");
            sb.append("题干：").append(nullToEmpty(q.getQuestionContent())).append("\n");
            sb.append("候选人回答：").append(nullToEmpty(q.getUserAnswer())).append("\n");
            String ref = q.getReferenceAnswer() != null ? q.getReferenceAnswer() : "无参考答案";
            sb.append("参考答案：").append(ref).append("\n");
            String rubric = q.getScoringRubric() != null ? q.getScoringRubric() : "无评分标准";
            sb.append("评分标准：").append(rubric).append("\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 兜底：保证分数合法，反馈不为空 */
    private EvaluationResponseVO sanitize(EvaluationResponseVO response) {
        Integer score = response.overallScore();
        if (score == null) {
            score = 0;
        }
        score = Math.max(0, Math.min(100, score));
        String feedback = response.feedback();
        if (feedback == null || feedback.isBlank()) {
            feedback = "暂未生成评估反馈，请结合面试表现与后续改进建议参考。";
        }
        return new EvaluationResponseVO(score, feedback);
    }

    /**
     * 查询面试评估结果（前端轮询）。
     * 校验会话存在与归属；status 为 COMPLETED 时携带总分与反馈，否则结果为空。
     */
    public InterviewEvaluationVO getEvaluation(String sessionId, Long userId) {
        InterviewSession session = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getSessionId, sessionId)
                .one();
        if (session == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "面试会话不存在");
        }
        AsyncTaskStatus status = session.getAsyncTaskStatus() == null
                ? AsyncTaskStatus.PENDING : session.getAsyncTaskStatus();

        boolean done = AsyncTaskStatus.COMPLETED.equals(status);
        return new InterviewEvaluationVO(
                session.getSessionId(),
                status,
                done ? session.getTotalScore() : null,
                done ? session.getFeedback() : null
        );
    }

    public InterviewSessionVO getSessionDetail(String sessionId, Long userId){
        InterviewSession session = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getSessionId, sessionId)
                .one();
        if (session == null) throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");

        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionIndex));
        List<InterviewQuestionVO> vos = questions.stream()
                .map(q -> new InterviewQuestionVO(
                        q.getQuestionIndex(),
                        q.getQuestionContent(),
                        q.getTopicSummary(),
                        q.getType(),
                        q.getUserAnswer()))
                .toList();

        return new InterviewSessionVO(
                session.getSessionId(),
                session.getUserId(),
                session.getCurrentIndex(),
                session.getStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeSession(String sessionId, Long userId) {
        // 1. 先校验会话是否存在，并且是否属于当前用户（防越权）
        InterviewSession session = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getSessionId, sessionId)
                .one();

        if (session == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在或无权删除");
        }

        // 2. 删除关联的题目和答案
        interviewQuestionMapper.delete(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId));

        // 3. 删除会话本身
        this.removeById(session.getSessionId());
    }

    public void updateAnswer(QuestionAnswer qa, Long userId) {
        // 1. 校验会话状态
        InterviewSession session = lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getSessionId, qa.sessionId())
                .one();
        if (session == null) throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        if (!InterviewSession.STATUS_CREATED.equals(session.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不可修改答案");
        }

        // 2. 只能修改历史题目（即当前进度之前的题）
        if (qa.currentIndex() >= session.getCurrentIndex()) {
            throw new BusinessException(HttpStatus.CONFLICT, "只能修改已提交的题目，当前题目请直接提交");
        }

        // 3. 更新题目答案
        boolean updated = interviewQuestionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, qa.sessionId())
                .eq(InterviewQuestion::getQuestionIndex, qa.currentIndex())
                .set(InterviewQuestion::getUserAnswer, qa.answer())
        ) > 0;

        if (!updated) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "题目不存在或修改失败");
        }
    }

    private String fetchResumeContent(Long resumeId){
        if(resumeId == null){
            return "";
        }
        Resume resume = resumeService.lambdaQuery()
                .eq(Resume::getId, resumeId)
                .orderByDesc(Resume::getId)
                .last("limit 1")
                .one();
        return resume != null && resume.getContent() != null ? resume.getContent() : "";
    }

    public record InterviewQuestionResponse(List<InterviewQuestionVO> questions) {}
}