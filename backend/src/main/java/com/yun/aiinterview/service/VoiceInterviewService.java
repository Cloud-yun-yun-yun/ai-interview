package com.yun.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yun.aiinterview.config.ResourcesProperties;
import com.yun.aiinterview.entity.VoiceInterviewSession;
import com.yun.aiinterview.entity.VoiceInterviewTurn;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.VoiceInterviewSessionMapper;
import com.yun.aiinterview.mapper.VoiceInterviewTurnMapper;
import com.yun.aiinterview.pojo.dto.VoiceInterviewSubmitDTO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewSessionVO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewTurnVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VoiceInterviewService extends ServiceImpl<VoiceInterviewSessionMapper, VoiceInterviewSession> {
    private final ChatClient chatClient;
    private final FileStorageService fileStorageService;
    private final AudioInitializerService audioInitializerService;
    private final VoiceInterviewSessionMapper voiceInterviewSessionMapper;
    private final QwenAsrService qwenAsrService;
    private final QwenTtsService qwenTtsService;
    private final PromptTemplate questionSystemPromptTemplate;
    private final PromptTemplate questionUserPromptTemplate;
    private final ResumeService resumeService;
    private final VoiceInterviewTurnMapper voiceInterviewTurnMapper;

    private static final int QUESTION_MAX_RETRY = 3;
    private static final long QUESTION_RETRY_BASE_DELAY_MS = 1000;

    public VoiceInterviewService(ChatClient chatClient,
                                 FileStorageService fileStorageService,
                                 AudioInitializerService audioInitializerService,
                                 VoiceInterviewSessionMapper voiceInterviewSessionMapper,
                                 QwenAsrService qwenAsrService,
                                 QwenTtsService qwenTtsService,
                                 ResourceLoader resourceLoader,
                                 ResourcesProperties properties,
                                 ResumeService resumeService,
                                 VoiceInterviewTurnMapper voiceInterviewTurnMapper) throws IOException{
        this.chatClient = chatClient;
        this.fileStorageService = fileStorageService;
        this.audioInitializerService = audioInitializerService;
        this.voiceInterviewSessionMapper = voiceInterviewSessionMapper;
        this.qwenAsrService = qwenAsrService;
        this.qwenTtsService = qwenTtsService;
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
        String sessionName = "0";
        VoiceInterviewSession vs = VoiceInterviewSession
                .builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionStatus(VoiceInterviewSession.STATUS_READY)
                .currentTurn(1)
                .totalTurns(9)
                .resumeId(resumeId)
                .introAudioKey(key)
                .sessionName(sessionName)
                .createTime(now)
                .build();
        int row = voiceInterviewSessionMapper.insert(vs);
        if(row!=1){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"数据库插入失败");
        }
        VoiceInterviewSessionVO vo = VoiceInterviewSessionVO
                .builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionStatus(VoiceInterviewSession.STATUS_READY)
                .currentTurn(1)
                .totalTurns(9)
                .resumeId(resumeId)
                .introAudioKey(key)
                .sessionName(sessionName)
                .createTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
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
        // 进度为 1（首题）且尚无回合记录时，返回固定开场白
        if (session.getCurrentTurn() == 1) {
            LambdaQueryWrapper<VoiceInterviewTurn> firstWrapper = new LambdaQueryWrapper<>();
            firstWrapper.eq(VoiceInterviewTurn::getTurnIndex, 1)
                    .eq(VoiceInterviewTurn::getSessionId, sessionId);
            VoiceInterviewTurn firstTurn = voiceInterviewTurnMapper.selectOne(firstWrapper);
            if (firstTurn == null) {
                return VoiceInterviewTurnVO
                        .builder()
                        .sessionId(sessionId)
                        .turnIndex(1)
                        .questionAudioKey(session.getIntroAudioKey())
                        .questionContent(AudioInitializerService.INTRO)
                        .build();
            }
        }
        LambdaQueryWrapper<VoiceInterviewTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoiceInterviewTurn::getTurnIndex,session.getCurrentTurn())
                .eq(VoiceInterviewTurn::getSessionId,sessionId);
        VoiceInterviewTurn turn = voiceInterviewTurnMapper.selectOne(wrapper);
        return VoiceInterviewTurnVO
                .builder()
                .sessionId(sessionId)
                .turnIndex(turn.getTurnIndex())
                .questionAudioKey(turn.getQuestionAudioKey())
                .questionContent(turn.getQuestionContent())
                .build();
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
                        .introAudioKey(s.getIntroAudioKey())
                        .createTime(s.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .sessionName(s.getSessionName())
                        .totalScore(s.getTotalScore())
                        .feedback(s.getFeedback())
                        .build()).toList();
        return vos;
    }
    
    public String uploadAudio(InputStream in){
        try {
            byte[] data = in.readAllBytes();
            String key = fileStorageService.uploadAudio(data);
            if(key==null){
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"上传失败");
            }
            return key;
        } catch (IOException e) {
            throw new RuntimeException("读取音频流失败", e);
        }
    }

    public VoiceInterviewTurnVO submit(Long userId,VoiceInterviewSubmitDTO dto){
        saveThisTurn(dto);
        VoiceInterviewSession session = getById(dto.sessionId());
        int totalTurns = session.getTotalTurns();

        if (dto.turnIndex() >= totalTurns) {
            lambdaUpdate()
                    .eq(VoiceInterviewSession::getSessionId, dto.sessionId())
                    .eq(VoiceInterviewSession::getUserId, userId)
                    .set(VoiceInterviewSession::getSessionStatus, VoiceInterviewSession.STATUS_FINISHED)
                    .update();
            return VoiceInterviewTurnVO
                    .builder()
                    .sessionId(dto.sessionId())
                    .turnIndex(dto.turnIndex() + 1)
                    .questionContent(AudioInitializerService.OUTRO)
                    .questionAudioKey(audioInitializerService.getOutroFileKey())
                    .build();
        }
        return getNextQuestion(userId, dto);
    }

    public void saveThisTurn(VoiceInterviewSubmitDTO dto){
        LambdaQueryWrapper<VoiceInterviewTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoiceInterviewTurn::getSessionId, dto.sessionId())
                .eq(VoiceInterviewTurn::getTurnIndex, dto.turnIndex());
        Long exists = voiceInterviewTurnMapper.selectCount(wrapper);
        if(exists != null && exists > 0){
            log.info("该回合已提交，跳过重复落库 sessionId={}, turnIndex={}", dto.sessionId(), dto.turnIndex());
            return;
        }

        log.info("开始获取用户回答音频");
        String audioKey = dto.userAudioKey();
        log.info("成功获取用户回答音频，audioKey={}",audioKey);
        log.info("准备发送请求，读取文本");
        byte[] data = fileStorageService.downloadFile(audioKey);
        String userAnswerText = qwenAsrService.transcribe(data);
        if (userAnswerText == null || userAnswerText.isBlank()) {
            userAnswerText = "用户未作答";
        }
        VoiceInterviewTurn turn = VoiceInterviewTurn
                .builder()
                .sessionId(dto.sessionId())
                .turnIndex(dto.turnIndex())
                .questionContent(dto.questionContent())
                .userAnswer(userAnswerText)
                .userAudioKey(audioKey)
                .questionAudioKey(dto.questionAudioKey())
                .isFollowUp(isFollowUpTurn(dto.turnIndex()))
                .build();
        int row = voiceInterviewTurnMapper.insert(turn);
        if(row==0){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"数据库操作失败");
        }
        log.info("本轮内容已成功插入数据库");
    }

    public VoiceInterviewTurnVO getNextQuestion(Long userId,VoiceInterviewSubmitDTO dto){
        log.info("准备调用ai，生成下轮面试题");
        HashMap<String, Object> map = new HashMap<>();
        String resumeContent = resumeService.getById(dto.resumeId()).getContent();
        if(resumeContent.trim().isEmpty()){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"简历不存在");
        }
        map.put("resumeContent", resumeContent);
        map.put("isFollowUp", isFollowUpTurn(dto.turnIndex()));
        map.put("qaRecords", buildQaRecords(dto.sessionId()));
        String user = questionUserPromptTemplate.render(map);
        String system = questionSystemPromptTemplate.render();
        log.info("提示词构建完毕");
        String question = generateQuestionWithRetry(user, system);
        log.info("问题生成完毕，开始转语音");
        byte[] questionData = qwenTtsService.synthesize(question);
        log.info("语音合成完毕");
        String questionAudioKey = fileStorageService.uploadAudio(questionData);
        //更新面试会话
        boolean isSuccess = lambdaUpdate()
                .eq(VoiceInterviewSession::getSessionId,dto.sessionId())
                .eq(VoiceInterviewSession::getUserId,userId)
                .set(VoiceInterviewSession::getCurrentTurn,dto.turnIndex()+1)
                .update();
        if(!isSuccess){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"更新面试会话失败");
        }
        VoiceInterviewTurnVO vo = VoiceInterviewTurnVO
                .builder()
                .sessionId(dto.sessionId())
                .turnIndex(dto.turnIndex()+1)
                .questionContent(question)
                .questionAudioKey(questionAudioKey)
                .build();
        return vo;
    }

    public byte[] downloadAudio(String fileKey){
        return fileStorageService.downloadFile(fileKey);
    }

    public void removeSession(Long userId, String sessionId){
        boolean isSuccess = lambdaQuery()
                .eq(VoiceInterviewSession::getSessionId,sessionId)
                .eq(VoiceInterviewSession::getUserId,userId).exists();
        if(!isSuccess){
            log.info("删除失败，没有匹配到对应的面试会话");
            throw new BusinessException(HttpStatus.BAD_REQUEST,"请选择正确的会话");
        }
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

    /**
     * 判断指定回合是否属于追问。
     * 规则：偶数回合（2,4,6…）为追问，奇数回合（1,3,5…）为普通新题。
     *
     * @param turnIndex 回合序号（从 1 开始）
     * @return 是否追问
     */
    private boolean isFollowUpTurn(int turnIndex) {
        return turnIndex % 2 == 0;
    }
}