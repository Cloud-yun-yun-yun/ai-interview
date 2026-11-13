package com.yun.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 语音面试相关配置（TTS 语音合成 + ASR 语音识别）。
 * 对应 application.yaml 中 voice.interview 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "voice.interview")
public class VoiceInterviewProperties {

    /** API Key */
    private String apiKey;

    /** 语音合成（TTS）配置 */
    private Tts tts;

    /** 语音识别（ASR）配置 */
    private Asr asr;

    @Data
    public static class Tts {
        /** 语音合成网关地址 */
        private String baseUrl;
        /** 模型名称 */
        private String model;
        /** 音频格式：wav / mp3 / pcm */
        private String format;
        /** 采样率 */
        private Integer sampleRate;
    }

    @Data
    public static class Asr {
        /** 语音识别网关地址 */
        private String baseUrl;
        /** 模型名称 */
        private String model;
        /** 音频格式：wav / mp3 */
        private String format;
        /** 采样率 */
        private Integer sampleRate;
    }
}