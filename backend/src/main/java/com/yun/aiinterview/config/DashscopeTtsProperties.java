package com.yun.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dashscope")
public class DashscopeTtsProperties {
    /** DashScope API Key */
    private String apiKey;
    /** 语音合成网关地址 */
    private String baseUrl;
    /** 文本转语音配置 */
    private Tts tts;

    @Data
    public static class Tts {
        /** 模型名称 */
        private String model;
        /** 音色 */
        private String voice;
        /** 音频格式：wav / mp3 / pcm */
        private String format;
        /** 采样率 */
        private Integer sampleRate;
        /** 声道数 */
        private Integer channels;
    }
}