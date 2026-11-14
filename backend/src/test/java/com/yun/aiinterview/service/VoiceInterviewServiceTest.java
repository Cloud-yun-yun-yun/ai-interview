package com.yun.aiinterview.service;

import com.yun.aiinterview.config.ResourcesProperties;
import com.yun.aiinterview.config.VoiceInterviewProperties;
import com.yun.aiinterview.entity.Resume;
import com.yun.aiinterview.entity.VoiceInterviewSession;
import com.yun.aiinterview.entity.VoiceInterviewTurn;
import com.yun.aiinterview.mapper.VoiceInterviewSessionMapper;
import com.yun.aiinterview.mapper.VoiceInterviewTurnMapper;
import com.yun.aiinterview.pojo.dto.VoiceInterviewSubmitDTO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewSessionVO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewTurnVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 语音面试核心业务流转测试。
 *
 * 思路：把会真实调用外部服务（AI 出题、ASR、TTS、MinIO 存储、开场白自启动合成）的依赖
 * 用 @MockBean 注释掉，仅保留真实 Mapper + 本地数据库，专注验证回合制状态流转与幂等逻辑。
 * 因此本测试不依赖真实 AI/语音/对象存储，可离线快速运行。
 */
@SpringBootTest
public class VoiceInterviewServiceTest {

    @Autowired
    private VoiceInterviewService voiceInterviewService;

    @Autowired
    private VoiceInterviewSessionMapper voiceInterviewSessionMapper;

    @Autowired
    private VoiceInterviewTurnMapper voiceInterviewTurnMapper;

    /**
     * 外部依赖容器上下文（开始不会真正跑）：AI 出题
     */
    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private ChatClient.ChatClientRequestSpec requestSpec;

    @MockitoBean
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * 语音识别（ASR）：换固定文本，跳过真实 HTTP 调用
     */
    @MockitoBean
    private QwenAsrService qwenAsrService;

    /**
     * 语音合成（TTS）：返回假音频字节，跳过真实 HTTP 调用
     */
    @MockitoBean
    private QwenTtsService qwenTtsService;

    /**
     * 文件存储（MinIO）：返回假 key，跳过真实对象存储
     */
    @MockitoBean
    private FileStorageService fileStorageService;

    /**
     * 简历服务：所属实体不在此表，返回固定简历实体
     */
    @MockitoBean
    private ResumeService resumeService;

    @MockitoBean
    private AudioInitializerService audioInitializerService;
    /** 测试用户 / 简历固定值 */
    private static final Long TEST_USER_ID = 999999L;
    private static final Long TEST_RESUME_ID = 999999L;

    /**
     * 需要 mock 的 ResourceLoader：构造 VoiceInterviewService 时会读取提示词模板。
     * 测试用内存模板内容代替真实文件，避免依赖 classpath 资源导致构造失败。
     */
    @MockitoBean
    private ResourceLoader resourceLoader;

    @BeforeEach
    void setUp() {
        // AI 出题链路：prompt().system().user().call().content() -> 固定题目
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("这是下一道面试题");

        // 开场白：固定 key
        when(audioInitializerService.getIntroFileKey()).thenReturn("voices/intro.mp3");

        // ASR：固定文本
        when(qwenAsrService.transcribe(any(byte[].class))).thenReturn("候选人回答内容");
        when(qwenAsrService.transcribe(any(byte[].class), anyString())).thenReturn("候选人回答内容");

        // TTS：假音频字节
        when(qwenTtsService.synthesize(anyString())).thenReturn(new byte[]{1, 2, 3});

        // MinIO：假 key / 假下载字节
        when(fileStorageService.uploadAudio(any(byte[].class))).thenReturn("voices/question1.mp3");
        when(fileStorageService.downloadFile(anyString())).thenReturn(new byte[]{10, 20, 30});

        // 简历：固定 content
        when(resumeService.getById(TEST_RESUME_ID))
                .thenReturn(Resume.builder().id(TEST_RESUME_ID).content("测试简历内容").build());

        // 提示词模板：内存内容替换
        when(resourceLoader.getResource(anyString()))
                .thenReturn(new ByteArrayResource("你是面试官".getBytes()));

        // 清理残留的测试数据，保证幂等可重复运行
        voiceInterviewTurnMapper.delete(null);
        voiceInterviewSessionMapper.delete(null);
    }

    @Test
    void createSession_应创建READY会话且进度为1() {
        VoiceInterviewSessionVO vo = voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);

        assertThat(vo.sessionId()).isNotBlank();
        assertThat(vo.sessionStatus()).isEqualTo(VoiceInterviewSession.STATUS_READY);
        assertThat(vo.currentTurn()).isEqualTo(1);
        assertThat(vo.totalTurns()).isEqualTo(9);
        assertThat(vo.introAudioKey()).isEqualTo("voices/intro.mp3");
    }

    @Test
    void createSession_存在进行中会话_应抛异常() {
        voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID)
        );
    }

    @Test
    void getCurrentTurn_进度1且无记录_应返回开场白() {
        VoiceInterviewSessionVO vo = voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);

        VoiceInterviewTurnVO turn = voiceInterviewService.getCurrentTurn(TEST_USER_ID, vo.sessionId());

        assertThat(turn.turnIndex()).isEqualTo(1);
        assertThat(turn.questionAudioKey()).isEqualTo("voices/intro.mp3");
    }

    @Test
    void submit_非末题_应返回下一题并推进进度() {
        VoiceInterviewSessionVO vo = voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);
        String sessionId = vo.sessionId();

        VoiceInterviewSubmitDTO dto = VoiceInterviewSubmitDTO.builder()
                .sessionId(sessionId)
                .turnIndex(1)
                .resumeId(TEST_RESUME_ID)
                .userAudioKey("voices/answer1.mp3")
                .questionAudioKey("voices/intro.mp3")
                .questionContent("开场白")
                .build();

        VoiceInterviewTurnVO next = voiceInterviewService.submit(TEST_USER_ID, dto);

        // 返回下一题（第 2 题）
        assertThat(next).isNotNull();
        assertThat(next.turnIndex()).isEqualTo(2);
        assertThat(next.questionContent()).isEqualTo("这是下一道面试题");

        // 进度已推进
        VoiceInterviewSession session = voiceInterviewSessionMapper.selectById(sessionId);
        assertThat(session.getCurrentTurn()).isEqualTo(2);

        // 本轮回答已落库
        VoiceInterviewTurn saved = voiceInterviewTurnMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .eq(VoiceInterviewTurn::getTurnIndex, 1));
        assertThat(saved.getUserAnswer()).isEqualTo("候选人回答内容");
        assertThat(saved.getIsFollowUp()).isFalse(); // 第1题不允许被追问
    }

    @Test
    void submit_末题第9题_应结束会话并返回null() {
        VoiceInterviewSessionVO vo = voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);
        String sessionId = vo.sessionId();
        // 构造 9 回合会话的进度，模拟已进行到最后一题
        voiceInterviewSessionMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoiceInterviewSession>()
                        .eq(VoiceInterviewSession::getSessionId, sessionId)
                        .set(VoiceInterviewSession::getCurrentTurn, 9));

        VoiceInterviewSubmitDTO dto = VoiceInterviewSubmitDTO.builder()
                .sessionId(sessionId)
                .turnIndex(9)
                .resumeId(TEST_RESUME_ID)
                .userAudioKey("voices/answer9.mp3")
                .questionAudioKey("voices/intro.mp3")
                .questionContent("第9题")
                .build();

        VoiceInterviewTurnVO result = voiceInterviewService.submit(TEST_USER_ID, dto);

        assertThat(result).isNull();

        VoiceInterviewSession session = voiceInterviewSessionMapper.selectById(sessionId);
        assertThat(session.getSessionStatus()).isEqualTo(VoiceInterviewSession.STATUS_FINISHED);
    }

    @Test
    void submit_同一轮重复提交_应幂等不重复落库() {
        VoiceInterviewSessionVO vo = voiceInterviewService.createSession(TEST_RESUME_ID, TEST_USER_ID);
        String sessionId = vo.sessionId();

        VoiceInterviewSubmitDTO dto = VoiceInterviewSubmitDTO.builder()
                .sessionId(sessionId)
                .turnIndex(1)
                .resumeId(TEST_RESUME_ID)
                .userAudioKey("voices/answer1.mp3")
                .questionAudioKey("voices/intro.mp3")
                .questionContent("开场白")
                .build();

        voiceInterviewService.submit(TEST_USER_ID, dto);
        voiceInterviewService.submit(TEST_USER_ID, dto);

        Long count = voiceInterviewTurnMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoiceInterviewTurn>()
                        .eq(VoiceInterviewTurn::getSessionId, sessionId)
                        .eq(VoiceInterviewTurn::getTurnIndex, 1));
        assertThat(count).isEqualTo(1L);

        // ASR 只应在首次提交时被调用，重复提交时不再调用
        verify(qwenAsrService, times(1)).transcribe(any(byte[].class));
    }

    private static java.io.InputStream in(byte[] data) {
        return new ByteArrayInputStream(data);
    }
}