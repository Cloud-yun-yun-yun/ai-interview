package com.yun.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yun.aiinterview.config.ResourcesProperties;
import com.yun.aiinterview.entity.Resume;
import com.yun.aiinterview.entity.VoiceInterviewSession;
import com.yun.aiinterview.entity.VoiceInterviewTurn;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.VoiceInterviewSessionMapper;
import com.yun.aiinterview.mapper.VoiceInterviewTurnMapper;
import com.yun.aiinterview.pojo.dto.ConsumerConfig;
import com.yun.aiinterview.pojo.dto.VoiceInterviewSubmitDTO;
import com.yun.aiinterview.pojo.vo.QuestionGenStatus;
import com.yun.aiinterview.pojo.vo.VoiceInterviewSessionVO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewTurnVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.StreamMessageId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yun.aiinterview.constant.AsyncTaskConstants.BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_SESSION_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_TURN_INDEX;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_USER_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.MAX_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_CLAIM_BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_IDLE_TIMEOUT_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.POLL_INTERVAL_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.VOICE_QUESTION_GEN_CONSUMER_PREFIX;
import static com.yun.aiinterview.constant.AsyncTaskConstants.VOICE_QUESTION_GEN_GROUP_NAME;
import static com.yun.aiinterview.constant.AsyncTaskConstants.VOICE_QUESTION_GEN_STREAM_KEY;

@Service
@Slf4j
public class VoiceInterviewService extends ServiceImpl<VoiceInterviewSessionMapper, VoiceInterviewSession> {
    private final ChatClient chatClient;
    private final FileStorageService fileStorageService;
    private final AudioInitializerService audioInitializerService;
    private final VoiceInterviewSessionMapper voiceInterviewSessionMapper;
    private final QwenTtsService qwenTtsService;
    private final QwenAsrService qwenAsrService;
    private final AsyncTaskStreamService asyncTaskStreamService;
    private final PromptTemplate questionSystemPromptTemplate;
    private final PromptTemplate questionUserPromptTemplate;
    private final ResumeService resumeService;
    private final VoiceInterviewTurnMapper voiceInterviewTurnMapper;

    private String consumerName;

    private static final String VOICE_QUESTION_GEN_THREAD_NAME = "voice-question-gen-consumer";
    private static final int QUESTION_MAX_RETRY = 3;
    private static final long QUESTION_RETRY_BASE_DELAY_MS = 1000;

    public VoiceInterviewService(ChatClient chatClient,
                                 FileStorageService fileStorageService,
                                 AudioInitializerService audioInitializerService,
                                 VoiceInterviewSessionMapper voiceInterviewSessionMapper,
                                 QwenTtsService qwenTtsService,
                                 QwenAsrService qwenAsrService,
                                 AsyncTaskStreamService asyncTaskStreamService,
                                 ResourceLoader resourceLoader,
                                 ResourcesProperties properties,
                                 ResumeService resumeService,
                                 VoiceInterviewTurnMapper voiceInterviewTurnMapper) throws IOException{
        this.chatClient = chatClient;
        this.fileStorageService = fileStorageService;
        this.audioInitializerService = audioInitializerService;
        this.voiceInterviewSessionMapper = voiceInterviewSessionMapper;
        this.qwenTtsService = qwenTtsService;
        this.qwenAsrService = qwenAsrService;
        this.asyncTaskStreamService = asyncTaskStreamService;
        this.questionSystemPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getVoiceQuestionSystem())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.questionUserPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getVoiceQuestionUer())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.resumeService = resumeService;
        this.voiceInterviewTurnMapper = voiceInterviewTurnMapper;
    }

    @Transactional
    public VoiceInterviewSessionVO createSession(Long resumeId, Long userId){
        boolean exists = lambdaQuery()
                .eq(VoiceInterviewSession::getResumeId, resumeId)
                .eq(VoiceInterviewSession::getSessionStatus,VoiceInterviewSession.STATUS_CREATED)
                .exists();
        if(exists){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"已存在进行中的语音面试，请先完成当前面试");
        }
        if(resumeId==null){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"请选择简历");
        }
        Resume resume = resumeService.lambdaQuery()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)
                .one();
        if(resume==null){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"请选择正确的简历");
        }
        if(!Resume.ACCEPT_INTERVIEW.equals(resume.getInterviewStatus())){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"简历状态错误，不能开启面试");
        }
        VoiceInterviewSession session = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId, userId)
                .eq(VoiceInterviewSession::getResumeId, resumeId).one();
        if(session!=null&&!VoiceInterviewSession.STATUS_FINISHED.equals(session.getSessionStatus())){
            log.info("创建语音面试会话失败，已有进行中的面试");
            throw new BusinessException(HttpStatus.BAD_REQUEST,"创建失败，已有进行中的面试");
        }
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String key = audioInitializerService.getIntroFileKey();
        String sessionName = resolveSessionName(resumeId);
        VoiceInterviewSession vs = VoiceInterviewSession
                .builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionStatus(VoiceInterviewSession.STATUS_CREATED)
                .currentTurn(1)
                .totalTurns(9)
                .resumeId(resumeId)
                .introAudioKey(key)
                .outroAudioKey(audioInitializerService.getOutroFileKey())
                .sessionName(sessionName)
                .questionGenStatus(QuestionGenStatus.NONE)
                .createTime(now)
                .build();
        int row = voiceInterviewSessionMapper.insert(vs);
        if(row!=1){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"数据库插入失败");
        }
        // 创建会话时即预建第一回合（开场白），把问题和音频 key 存入 turn 表
        VoiceInterviewTurn firstTurn = VoiceInterviewTurn
                .builder()
                .sessionId(sessionId)
                .turnIndex(1)
                .questionContent(AudioInitializerService.INTRO)
                .questionAudioKey(key)
                .build();
        int turnRow = voiceInterviewTurnMapper.insert(firstTurn);
        if(turnRow != 1){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"回合记录插入失败");
        }
        String url = fileStorageService.generatePresignedUrl(key);
        VoiceInterviewSessionVO vo = VoiceInterviewSessionVO
                .builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionStatus(VoiceInterviewSession.STATUS_CREATED)
                .currentTurn(1)
                .totalTurns(9)
                .resumeId(resumeId)
                .introAudioUrl(url)
                .sessionName(sessionName)
                .createTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .type("voice")
                .questionGenStatus(QuestionGenStatus.NONE)
                .build();
        return vo;
    }

    public VoiceInterviewTurnVO getCurrentTurn(Long userId,String sessionId){
        VoiceInterviewSession session = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId, userId)
                .eq(VoiceInterviewSession::getSessionId, sessionId).one();
        if(session==null){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"面试会话不存在");
        }
        // 直接取当前回合记录（创作会话/生成问题时已预建）
        LambdaQueryWrapper<VoiceInterviewTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoiceInterviewTurn::getTurnIndex,session.getCurrentTurn())
                .eq(VoiceInterviewTurn::getSessionId,sessionId);
        VoiceInterviewTurn turn = voiceInterviewTurnMapper.selectOne(wrapper);
        if(turn == null){
            throw new BusinessException(HttpStatus.NOT_FOUND,"当前回合不存在");
        }
        String url = turn.getQuestionAudioKey() != null
                ? fileStorageService.generatePresignedUrl(turn.getQuestionAudioKey())
                : null;
        log.info("当前回合音频 URL: {}",url);
        return VoiceInterviewTurnVO
                .builder()
                .sessionId(sessionId)
                .turnIndex(turn.getTurnIndex())
                .questionAudioUrl(url)
                .questionContent(turn.getQuestionContent())
                .build();
    }

    public VoiceInterviewSessionVO getSession(Long userId,String sessionId){
        VoiceInterviewSession session = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId,userId)
                .eq(VoiceInterviewSession::getSessionId,sessionId).one();
        if(session==null){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"面试会话不存在");
        }
        VoiceInterviewSessionVO vo = VoiceInterviewSessionVO.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .currentTurn(session.getCurrentTurn())
                .totalTurns(session.getTotalTurns())
                .sessionStatus(session.getSessionStatus())
                .evaluationStatus(session.getEvaluationStatus())
                .resumeId(session.getResumeId())
                .createTime(session.getCreateTime() != null ? session.getCreateTime().toString() : null)
                .sessionName(session.getSessionName())
                .totalScore(session.getTotalScore())
                .feedback(session.getFeedback())
                .type(session.getType())
                .questionGenStatus(session.getQuestionGenStatus())
                .outroAudioUrl(VoiceInterviewSession.STATUS_FINISHED.equals(session.getSessionStatus())
                        ? fileStorageService.generatePresignedUrl(resolveOutroAudioKey(session))
                        : null)
                .build();
        return vo;
    }

    public List<VoiceInterviewSessionVO> getSessionList(Long userId){
        List<VoiceInterviewSession> sessions = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId, userId)
                .orderByDesc(VoiceInterviewSession::getCreateTime)
                .list();
        List<VoiceInterviewSessionVO> vos = sessions.stream()
                .map(s -> VoiceInterviewSessionVO
                        .builder()
                        .sessionId(s.getSessionId())
                        .userId(s.getUserId())
                        .currentTurn(s.getCurrentTurn())
                        .totalTurns(s.getTotalTurns())
                        .sessionStatus(s.getSessionStatus())
                        .evaluationStatus(s.getEvaluationStatus())
                        .resumeId(s.getResumeId())
                        .introAudioUrl(fileStorageService.generatePresignedUrl(s.getIntroAudioKey()))
                        .createTime(s.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .sessionName(s.getSessionName())
                        .totalScore(s.getTotalScore())
                        .feedback(s.getFeedback())
                        .type(s.getType())
                        .questionGenStatus(s.getQuestionGenStatus())
                        .build()).toList();
        return vos;
    }
    
    public String uploadAudio(Long userId, String sessionId, byte[] data, String filename){
        String key = uploadVoiceAudioTo(userId, sessionId, data, filename);
        if(key==null){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"上传失败");
        }
        return key;
    }

    /**
     * 用户回答 / 题目音频上传到结构化 key，便于按会话精准删除。
     * key 形如：ai-interview/voiceInterview/{userId}/{sessionId}/{uuid}.{ext}
     */
    private String uploadVoiceAudioTo(Long userId, String sessionId, byte[] data, String filename){
        String ext = resolveExt(filename);
        String fileKey = "ai-interview/voiceInterview/" + userId + "/" + sessionId + "/"
                + UUID.randomUUID() + (ext == null ? "" : "." + ext);
        String contentType = resolveAudioContentType(ext);
        return fileStorageService.uploadTo(data, fileKey, contentType);
    }

    private String resolveExt(String filename){
        if(filename == null) return null;
        int idx = filename.lastIndexOf('.');
        if(idx < 0 || idx == filename.length() - 1) return null;
        return filename.substring(idx + 1).toLowerCase();
    }

    private String resolveAudioContentType(String ext){
        if(ext == null) return "audio/webm";
        return switch (ext) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "ogg" -> "audio/ogg";
            default -> "audio/webm";
        };
    }

    @Transactional
    public VoiceInterviewSessionVO submit(Long userId, VoiceInterviewSubmitDTO dto) {
        VoiceInterviewSession session = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId, userId)
                .eq(VoiceInterviewSession::getSessionId, dto.sessionId())
                .one();
        if (session == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "面试会话不存在");
        }
        if (!session.getCurrentTurn().equals(dto.turnIndex())) {
            throw new BusinessException(HttpStatus.CONFLICT, "提交的回合与当前进度不一致");
        }
        if (QuestionGenStatus.QUEUED.equals(session.getQuestionGenStatus())
                || QuestionGenStatus.PROCESSING.equals(session.getQuestionGenStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "上一题正在出题中，请稍候");
        }

        // 1. 保存本轮用户回答音频 key（ASR 在异步任务里读取）
        int row = voiceInterviewTurnMapper.update(null,
                new LambdaUpdateWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, dto.sessionId())
                        .eq(VoiceInterviewTurn::getTurnIndex, dto.turnIndex())
                        .set(VoiceInterviewTurn::getUserAudioKey, dto.userAudioKey()));
        if (row != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "保存回答音频失败");
        }

        if (dto.turnIndex() >= session.getTotalTurns()) {
            //直接asr解析文本
            byte[] data = fileStorageService.downloadFile(dto.userAudioKey());
            String userAnswer = qwenAsrService.transcribe(data,resolveAudioFormat(dto.userAudioKey()));
            voiceInterviewTurnMapper.update(null,
                    new LambdaUpdateWrapper<VoiceInterviewTurn>()
                            .eq(VoiceInterviewTurn::getSessionId, dto.sessionId())
                            .eq(VoiceInterviewTurn::getTurnIndex, dto.turnIndex())
                            .set(VoiceInterviewTurn::getUserAnswer, userAnswer));
            // 末题：结束会话，无异步出题任务
            lambdaUpdate()
                    .eq(VoiceInterviewSession::getSessionId, dto.sessionId())
                    .eq(VoiceInterviewSession::getUserId, userId)
                    .set(VoiceInterviewSession::getSessionStatus, VoiceInterviewSession.STATUS_FINISHED)
                    .set(VoiceInterviewSession::getQuestionGenStatus, QuestionGenStatus.COMPLETED)
                    .update();

            return getSession(userId, dto.sessionId());
        }

        // 2. 置为 QUEUED（出题中）
        updateQuestionGenStatus(dto.sessionId(), QuestionGenStatus.QUEUED);

        // 3. 事务提交后再入队，确保消费端能读到已提交的 userAudioKey 与 QUEUED 状态
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendVoiceQuestionGenTask(dto.sessionId(), userId, dto.turnIndex(), 0);
            }
        });

        return getSession(userId, dto.sessionId());
    }

    @PostConstruct
    public void init() {
        this.consumerName = VOICE_QUESTION_GEN_CONSUMER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        asyncTaskStreamService.registerConsumer(
                new ConsumerConfig(
                        VOICE_QUESTION_GEN_STREAM_KEY,
                        VOICE_QUESTION_GEN_GROUP_NAME,
                        consumerName,
                        VOICE_QUESTION_GEN_THREAD_NAME,
                        BATCH_SIZE,
                        POLL_INTERVAL_MS,
                        PENDING_IDLE_TIMEOUT_MS,
                        PENDING_CLAIM_BATCH_SIZE
                ),
                this::processVoiceQuestionGenMessage
        );
    }

    public void sendVoiceQuestionGenTask(String sessionId, Long userId, Integer turnIndex, int retryCount) {
        Map<String, String> messages = Map.of(
                FIELD_SESSION_ID, sessionId,
                FIELD_USER_ID, userId.toString(),
                FIELD_TURN_INDEX, String.valueOf(turnIndex),
                FIELD_RETRY_COUNT, String.valueOf(retryCount)
        );
        try {
            String messageId = asyncTaskStreamService.sendTask(VOICE_QUESTION_GEN_STREAM_KEY, messages);
            log.info("语音面试出题任务已发送到Stream, messageId={}", messageId);
        } catch (Exception e) {
            log.warn("语音面试出题任务入队失败，置为 FAILED, sessionId={}", sessionId, e);
            updateQuestionGenStatus(sessionId, QuestionGenStatus.FAILED);
        }
    }

    private void processVoiceQuestionGenMessage(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(FIELD_SESSION_ID);
        String userIdStr = data.get(FIELD_USER_ID);
        String turnIndexStr = data.get(FIELD_TURN_INDEX);
        String retryCountStr = data.get(FIELD_RETRY_COUNT);
        if (sessionId == null || userIdStr == null || turnIndexStr == null) {
            log.warn("语音面试出题消息格式错误，丢弃: messageId={}", messageId);
            asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
            return;
        }
        Long userId = Long.parseLong(userIdStr);
        int submittedTurnIndex = Integer.parseInt(turnIndexStr);
        try {
            VoiceInterviewSession session = getById(sessionId);
            if (session == null) {
                log.warn("语音面试会话不存在，丢弃: messageId={}", messageId);
                asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
                return;
            }
            if (!userId.equals(session.getUserId())) {
                log.warn("语音面试出题消息归属不匹配，丢弃: messageId={}", messageId);
                asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
                return;
            }
            if (QuestionGenStatus.COMPLETED.equals(session.getQuestionGenStatus())) {
                log.info("题目已生成，跳过重复消息: sessionId={}", sessionId);
                asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
                return;
            }

            updateQuestionGenStatus(sessionId, QuestionGenStatus.PROCESSING);

            generateNextTurn(userId, sessionId, submittedTurnIndex);

            updateQuestionGenStatus(sessionId, QuestionGenStatus.COMPLETED);

            asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
        } catch (Exception e) {
            try {
                log.error("语音面试出题失败，sessionId={}", sessionId, e);
                int retryCount = 0;
                try {
                    retryCount = retryCountStr == null ? 0 : Integer.parseInt(retryCountStr);
                } catch (NumberFormatException nfe) {
                    retryCount = MAX_RETRY_COUNT;
                }
                if (retryCount >= MAX_RETRY_COUNT) {
                    updateQuestionGenStatus(sessionId, QuestionGenStatus.FAILED);
                } else {
                    updateQuestionGenStatus(sessionId, QuestionGenStatus.QUEUED);
                    sendVoiceQuestionGenTask(sessionId, userId, submittedTurnIndex, retryCount + 1);
                }
            } finally {
                asyncTaskStreamService.ackMessage(VOICE_QUESTION_GEN_STREAM_KEY, VOICE_QUESTION_GEN_GROUP_NAME, messageId);
            }
        }
    }

    /**
     * 异步生成下一回合：ASR 识别用户回答 -> LLM 出题(仅文本) -> 落库 -> 非流式合成习题音频 -> 推进进度。
     */
    private void generateNextTurn(Long userId, String sessionId, Integer submittedTurnIndex) {
        // 幂等：下一回合已生成则直接推进进度
        VoiceInterviewTurn existingNext = voiceInterviewTurnMapper.selectOne(
                new LambdaQueryWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .eq(VoiceInterviewTurn::getTurnIndex, submittedTurnIndex + 1));
        if (existingNext != null) {
            log.info("下一回合已生成，跳过: sessionId={}, turnIndex={}", sessionId, submittedTurnIndex + 1);
            updateCurrentTurn(sessionId, userId, submittedTurnIndex + 1);
            return;
        }

        // 1. ASR：下载用户音频 -> 转写 -> 回写 userAnswer
        VoiceInterviewTurn currentTurn = voiceInterviewTurnMapper.selectOne(
                new LambdaQueryWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .eq(VoiceInterviewTurn::getTurnIndex, submittedTurnIndex));
        if (currentTurn == null || currentTurn.getUserAudioKey() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "未找到用户音频，无法识别");
        }
        byte[] audio = fileStorageService.downloadFile(currentTurn.getUserAudioKey());
        String answerText = qwenAsrService.transcribe(audio, resolveAudioFormat(currentTurn.getUserAudioKey()));
        voiceInterviewTurnMapper.update(null,
                new LambdaUpdateWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .eq(VoiceInterviewTurn::getTurnIndex, submittedTurnIndex)
                        .set(VoiceInterviewTurn::getUserAnswer, answerText));
        log.info("ASR 识别完成并回写: sessionId={}, turnIndex={}", sessionId, submittedTurnIndex);

        // 2. LLM 生成下一题
        VoiceInterviewSession session = getById(sessionId);
        Resume resume = resumeService.getById(session.getResumeId());
        if (resume == null || resume.getContent() == null || resume.getContent().trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "简历不存在");
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("resumeContent", resume.getContent());
        map.put("qaRecords", buildQaRecords(sessionId));
        String user = questionUserPromptTemplate.render(map);
        String system = questionSystemPromptTemplate.render();
        String question = generateQuestionWithRetry(user, system);

        // 3. 落库下一回合（仅存文本；音频由非流式 TTS 合成后回填 key）
        VoiceInterviewTurn nextTurn = VoiceInterviewTurn.builder()
                .sessionId(sessionId)
                .turnIndex(submittedTurnIndex + 1)
                .questionContent(question)
                .build();
        int turnRow = voiceInterviewTurnMapper.insert(nextTurn);
        if (turnRow != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "回合记录插入失败");
        }
        // 3.1 后端非流式合成当前回合习题音频并上传 minio，回填 questionAudioKey
        synthesizeQuestionAudio(userId, sessionId, nextTurn, question);
        updateCurrentTurn(sessionId, userId, submittedTurnIndex + 1);
    }

    /**
     * 非流式 TTS 合成当前回合习题音频：synthesizeSync 合成 -> 上传 minio -> 回填 questionAudioKey。
     * 供前端轮询到 COMPLETED 后通过 getCurrentTurn 直接拿到音频 URL 播放。
     */
    private void synthesizeQuestionAudio(Long userId, String sessionId, VoiceInterviewTurn turn, String text) {
        try {
            byte[] audio = qwenTtsService.synthesizeSync(text);
            if (audio == null || audio.length == 0) {
                log.warn("非流式 TTS 返回空音频，跳过回填, sessionId={}, turn={}", sessionId, turn.getTurnIndex());
                return;
            }
            String audioKey = uploadVoiceAudioTo(userId, sessionId, audio, "question.wav");
            turn.setQuestionAudioKey(audioKey);
            voiceInterviewTurnMapper.updateById(turn);
            log.info("非流式 TTS 习题音频已上传落库, sessionId={}, turn={}, key={}",
                    sessionId, turn.getTurnIndex(), audioKey);
        } catch (Exception e) {
            log.error("非流式 TTS 合成习题音频失败, sessionId={}, turn={}", sessionId, turn.getTurnIndex(), e);
        }
    }

    private void updateQuestionGenStatus(String sessionId, QuestionGenStatus status) {
        VoiceInterviewSession update = VoiceInterviewSession.builder()
                .sessionId(sessionId)
                .questionGenStatus(status)
                .build();
        voiceInterviewSessionMapper.updateById(update);
    }

    private void updateCurrentTurn(String sessionId, Long userId, int turnIndex) {
        boolean ok = lambdaUpdate()
                .eq(VoiceInterviewSession::getSessionId, sessionId)
                .eq(VoiceInterviewSession::getUserId, userId)
                .set(VoiceInterviewSession::getCurrentTurn, turnIndex)
                .update();
        if (!ok) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "更新面试会话进度失败");
        }
    }

    private String resolveAudioFormat(String key) {
        if (key == null) {
            return "mp3";
        }
        String lower = key.toLowerCase();
        if (lower.endsWith(".wav")) return "wav";
        if (lower.endsWith(".mp3")) return "mp3";
        if (lower.endsWith(".m4a")) return "m4a";
        if (lower.endsWith(".ogg")) return "ogg";
        return "webm";
    }

    /** 取结语音频 key：优先用会话存储值，历史会话未存时回退到固定结语 key */
    private String resolveOutroAudioKey(VoiceInterviewSession session) {
        String key = session.getOutroAudioKey();
        if (key == null || key.isBlank()) {
            key = audioInitializerService.getOutroFileKey();
        }
        return key;
    }



    public byte[] downloadAudio(String fileKey){
        return fileStorageService.downloadFile(fileKey);
    }

    public void removeSession(Long userId, String sessionId){
        VoiceInterviewSession session = lambdaQuery()
                .eq(VoiceInterviewSession::getUserId, userId)
                .eq(VoiceInterviewSession::getSessionId, sessionId)
                .one();
        if(session == null){
            log.info("删除失败，没有匹配到对应的面试会话");
            throw new BusinessException(HttpStatus.BAD_REQUEST,"请选择正确的会话");
        }
        // 仅允许删除已结束（FINISHED）的会话
        if(!VoiceInterviewSession.STATUS_FINISHED.equals(session.getSessionStatus())){
            throw new BusinessException(HttpStatus.CONFLICT, "仅已结束的面试可删除");
        }
        // 删除该会话在对象存储中的全部音频（按会话前缀精准删除）
        fileStorageService.deleteByPrefix("ai-interview/voiceInterview/" + userId + "/" + sessionId);
        // 删除关联的回合记录
        voiceInterviewTurnMapper.delete(new LambdaQueryWrapper<VoiceInterviewTurn>()
                .eq(VoiceInterviewTurn::getSessionId, sessionId));
        // 删除会话本身
        boolean isSuccess = lambdaUpdate()
                .eq(VoiceInterviewSession::getSessionId, sessionId)
                .eq(VoiceInterviewSession::getUserId, userId)
                .remove();
        if(!isSuccess){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"删除失败，请重试");
        }
        log.info("删除成功，会话ID: {}", sessionId);
    }

    /**
     * 生成语音面试会话名称：简历名 + 语音面试。
     */
    private String resolveSessionName(Long resumeId) {
        if (resumeId == null) {
            return "语音面试";
        }
        Resume resume = resumeService.getById(resumeId);
        if (resume == null || resume.getName() == null || resume.getName().isBlank()) {
            return "语音面试";
        }
        return resume.getName() + "语音面试";
    }

    /**
     * 调用 AI 生成题目，失败（异常或空白结果）时按指数退避重试。
     *
     * @param user   user 提示词
     * @param system system 提示词
     * @return 生成的题目文本
     */
    private String generateQuestionWithRetry(String user, String system) {
        int attempt = 0;
        BusinessException lastException = null;
        while (attempt < QUESTION_MAX_RETRY) {
            attempt++;
            try {
                String question = chatClient.prompt().system(system).user(user).call().content();
                if (question == null || question.isBlank()) {
                    log.warn("AI生成题目为空，第 {}/{} 次重试", attempt, QUESTION_MAX_RETRY);
                    lastException = new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI生成题目结果为空");
                } else {
                    return question;
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.warn("AI生成题目异常，第 {}/{} 次重试", attempt, QUESTION_MAX_RETRY, e);
                lastException = new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI生成题目失败");
            }
            if (attempt < QUESTION_MAX_RETRY) {
                try {
                    Thread.sleep(QUESTION_RETRY_BASE_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI生成题目流程被中断");
                }
            }
        }
        log.error("AI生成题目重试 {} 次后仍失败", QUESTION_MAX_RETRY);
        throw lastException;
    }

    private String buildQaRecords(String sessionId) {
        List<VoiceInterviewTurn> turns = voiceInterviewTurnMapper.selectList(
                new LambdaQueryWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .orderByAsc(VoiceInterviewTurn::getTurnIndex));

        StringBuilder sb = new StringBuilder();
        for (VoiceInterviewTurn t : turns) {
            sb.append("第").append(t.getTurnIndex()).append("题");
            sb.append("：\n");
            sb.append("题干：").append(nullToEmpty(t.getQuestionContent())).append("\n");
            sb.append("候选人回答：").append(nullToEmpty(t.getUserAnswer())).append("\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}