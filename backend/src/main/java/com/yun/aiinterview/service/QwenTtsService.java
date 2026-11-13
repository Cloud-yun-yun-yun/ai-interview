package com.yun.aiinterview.service;

import cn.hutool.http.HttpUtil;
import com.yun.aiinterview.config.VoiceInterviewProperties;
import com.yun.aiinterview.pojo.dto.QwenTtsRequestDTO;
import com.yun.aiinterview.pojo.dto.QwenTtsResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * DashScope 语音合成（TTS）工具类。
 * 基于官方 REST 同步接口：POST {baseUrl} 文本 -> 返回音频URL -> GET 下载音频。
 * 请求/响应用实体类自动序列化与反序列化（RestClient + Jackson）。
 */
@Slf4j
@Service
public class QwenTtsService {

    private final VoiceInterviewProperties properties;
    private final RestClient restClient;

    public QwenTtsService(VoiceInterviewProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 同步合成：文本转成完整的音频字节数组。
     *
     * @param text 待合成的文本
     * @return 音频字节（格式由配置决定，默认 mp3）
     * @throws IllegalStateException 合成失败或 API Key 未配置
     */
    public byte[] synthesize(String text) {
        String apiKey = resolveApiKey();
        String baseUrl = properties.getTts().getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("未配置 DashScope TTS base-url，请在 application.yaml 设置 voice.interview.tts.base-url");
        }

        String format = properties.getTts().getFormat();
        // mp3 编码固定为单声道，不带 channels；wav/pcm 才带声道
        Integer channels = "mp3".equalsIgnoreCase(format) ? null : 1;
        QwenTtsRequestDTO request = QwenTtsRequestDTO.of(
                properties.getTts().getModel(),
                text,
                format,
                properties.getTts().getSampleRate(),
                channels);

        log.info("调用 DashScope TTS, model={}, format={}, text={}",
                properties.getTts().getModel(), format, text);

        // 1. POST 请求，实体类自动序列化；响应自动反序列化为 TtsResponseDTO
        QwenTtsResponseDTO response;
        try {
            response = restClient.post()
                    .uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(QwenTtsResponseDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("TTS 请求失败: " + e.getMessage(), e);
        }

        // 2. 取出音频下载地址
        String audioUrl = null;
        if (response != null && response.getOutput() != null
                && response.getOutput().getAudio() != null) {
            audioUrl = response.getOutput().getAudio().getUrl();
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalStateException("TTS 响应中未包含音频地址");
        }

        log.info("TTS 合成成功，下载音频: {}", audioUrl);

        // 3. 下载音频字节
        try {
            byte[] audio = HttpUtil.downloadBytes(audioUrl);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("TTS 下载到的音频为空");
            }
            log.info("TTS 音频下载完成, size={} bytes", audio.length);
            return audio;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("TTS 音频下载失败: " + e.getMessage(), e);
        }
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