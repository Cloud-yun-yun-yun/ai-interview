package com.yun.aiinterview.service;

import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.yun.aiinterview.config.VoiceInterviewProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;

/**
 * DashScope 语音合成（TTS）工具类。
 * 基于官方 HttpSpeechSynthesizer SDK：
 * 非流式合成 callAndReturnAudio：阻塞等待合成完成后返回整段音频字节；
 * 模型 / 音色 / 格式 / 采样率等均从 application.yaml 的 voice.interview.tts 读取。
 */
@Slf4j
@Service
public class QwenTtsService {

    private final VoiceInterviewProperties properties;

    public QwenTtsService(VoiceInterviewProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initBaseUrl() {
        String baseUrl = properties.getTts().getBaseUrl();
        if (StringUtils.hasText(baseUrl)) {
            // SDK 默认走 dashscope.aliyuncs.com，这里切换到配置的 MaaS 网关
            Constants.baseHttpApiUrl = baseUrl;
            log.info("DashScope SDK baseHttpApiUrl 已设为: {}", baseUrl);
        }
    }

    /** 模型名称 */
    private String model() {
        return properties.getTts().getModel();
    }

    /** 音色 */
    private String voice() {
        return properties.getTts().getVoice();
    }

    /** 音频格式：wav / mp3 / pcm */
    private String format() {
        return properties.getTts().getFormat();
    }

    /** 采样率 */
    private Integer sampleRate() {
        return properties.getTts().getSampleRate();
    }

    /**
     * 非流式语音合成：阻塞等待合成完成，一次性返回完整音频字节。
     * 使用官方 SDK 的 callAndReturnAudio（返回 ByteBuffer 完整音频数据），非 Base64/URL。
     *
     * @param text 待合成的文本
     * @return 完整音频字节（wav），若合成结果为空返回 null
     */
    public byte[] synthesizeSync(String text) {
        String apiKey = resolveApiKey();
        HttpSpeechSynthesisParam param = buildParam(text, apiKey);

        log.info("调用非流式 TTS(SDK callAndReturnAudio), model={}, voice={}, format={}",
                model(), voice(), format());

        try {
            HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
            ByteBuffer audioData = synthesizer.callAndReturnAudio(param);
            if (audioData == null || !audioData.hasRemaining()) {
                log.warn("非流式 TTS 返回空音频, text={}", text);
                return null;
            }
            byte[] audio = new byte[audioData.remaining()];  // ① 先申请一个 byte 数组
            audioData.get(audio);                            // ② 把 ByteBuffer 里剩余的数据"拷贝"进这个数组
            log.info("非流式 TTS 合成完成, 音频字节={}", audio.length);
            return audio;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("非流式 TTS 请求失败: {}", e.getMessage(), e);
            throw new IllegalStateException("非流式 TTS 请求失败: " + e.getMessage(), e);
        }
    }

    /** 构建合成参数，非流式 / 流式共用 */
    private HttpSpeechSynthesisParam buildParam(String text, String apiKey) {
        return HttpSpeechSynthesisParam.builder()
                .model(model())
                .text(text)
                .voice(voice())
                .format(format())
                .sampleRate(sampleRate())
                .apiKey(apiKey)
                .build();
    }

    private String resolveApiKey() {
        // 1. 优先取配置文件中的 api-key
        if (StringUtils.hasText(properties.getApiKey())) {
            return properties.getApiKey().trim();
        }
        // 2. 兜底取环境变量
        String envKey = System.getenv("DASHSCOPE_API_KEY");
        if (StringUtils.hasText(envKey)) {
            return envKey;
        }
        throw new IllegalStateException("未配置语音 API Key，请在 application.yaml 设置 voice.interview.api-key 或环境变量 DASHSCOPE_API_KEY");
    }
}