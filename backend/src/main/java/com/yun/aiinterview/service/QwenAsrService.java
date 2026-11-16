package com.yun.aiinterview.service;

import com.yun.aiinterview.config.VoiceInterviewProperties;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.pojo.dto.QwenAsrRequestDTO;
import com.yun.aiinterview.pojo.dto.QwenAsrResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * DashScope Qwen-Audio 语音识别（ASR）工具类。
 * 走多模态原生 HTTP 接口：音频 base64 内嵌在 input_audio，无需上传获取公网 URL。
 * 请求/响应用实体类自动序列化与反序列化（RestClient + Jackson）。
 */
@Slf4j
@Service
public class QwenAsrService {

    private final VoiceInterviewProperties properties;
    private final RestClient restClient;

    public QwenAsrService(VoiceInterviewProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 语音识别：音频字节 -> 识别文本（同步阻塞）。
     * @param audio  音频字节
     * @return 识别文本
     */
    public String transcribe(byte[] audio){
        return transcribe(audio, properties.getAsr().getFormat());
    }
    public String transcribe(byte[] audio, String format) {
        String apiKey = properties.getApiKey();
        String baseUrl = properties.getAsr().getBaseUrl();
        String model = properties.getAsr().getModel();
        // wav 的采样率内嵌在文件头，交给模型从文件头读取，避免与录制时的实际采样率冲突
        Integer sampleRate = "wav".equalsIgnoreCase(format) ? null : properties.getAsr().getSampleRate();

        String mime = "audio/" + format;
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audio);

        QwenAsrRequestDTO request = QwenAsrRequestDTO.of(model, dataUri, format, sampleRate);

        log.info("调用 DashScope ASR 识别, model={}, 音频大小={} bytes, format={}", model, audio.length, format);

        QwenAsrResponseDTO response;
        try {
            response = restClient.post()
                    .uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    // 显式关闭 SSE，走同步阻塞返回：一次请求直接拿完整识别文本
                    .header("X-DashScope-SSE", "disable")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(QwenAsrResponseDTO.class);
        } catch (Exception e) {
            log.error("ASR 请求失败", e);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "语音识别请求失败: " + e.getMessage());
        }

        String text = extractText(response);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "语音识别未返回有效文本");
        }
        log.info("ASR 识别完成: {}", text);
        return text;
    }

    /** 从响应中提取识别文本 */
    private String extractText(QwenAsrResponseDTO response) {
        if (response == null || response.getOutput() == null
                || response.getOutput().getSentence() == null) {
            return null;
        }
        return response.getOutput().getSentence().getText();
    }
}