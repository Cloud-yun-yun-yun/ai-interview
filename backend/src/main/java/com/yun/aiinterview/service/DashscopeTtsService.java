package com.yun.aiinterview.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yun.aiinterview.config.DashscopeTtsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DashScope 语音合成（TTS）工具类。
 * 基于官方 REST 同步接口：POST {baseUrl} 文本 -> 返回音频URL -> GET 下载音频。
 */
@Slf4j
@Service
public class DashscopeTtsService {

    private final DashscopeTtsProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashscopeTtsService(DashscopeTtsProperties properties) {
        this.properties = properties;
    }

    /**
     * 同步合成：文本转成完整的音频字节数组。
     *
     * @param text 待合成的文本
     * @return 音频字节（格式由配置决定，默认 wav）
     * @throws IllegalStateException 合成失败或 API Key 未配置
     */
    public byte[] synthesize(String text) {
        String apiKey = resolveApiKey();
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("未配置 DashScope TTS base-url，请在 application.yaml 设置 dashscope.base-url");
        }

        // 1. 构造请求体：{model, input:{text_prompt, format, sample_rate, channels}}
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text_prompt", text);
        input.put("format", properties.getTts().getFormat());
        input.put("sample_rate", properties.getTts().getSampleRate());
        input.put("channels", properties.getTts().getChannels() == null ? 2 : properties.getTts().getChannels());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getTts().getModel());
        body.put("input", input);

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构造 TTS 请求体失败", e);
        }

        log.info("调用 DashScope TTS, model={}, text={}", properties.getTts().getModel(), text);

        // 2. POST 请求，拿到返回的音频下载 URL
        String respBody;
        try {
            respBody = HttpRequest.post(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(bodyJson)
                    .timeout(300_000)
                    .execute()
                    .body();
        } catch (Exception e) {
            throw new IllegalStateException("TTS 请求失败: " + e.getMessage(), e);
        }

        // 3. 解析 output.audio.url
        String audioUrl;
        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode urlNode = root.path("output").path("audio").path("url");
            if (urlNode.isMissingNode() || !StringUtils.hasText(urlNode.asText())) {
                log.error("TTS 返回异常响应: {}", respBody);
                throw new IllegalStateException("TTS 响应中未包含音频地址");
            }
            audioUrl = urlNode.asText();
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("解析 TTS 响应失败: " + e.getMessage() + ", 原始响应: " + respBody, e);
        }

        log.info("TTS 合成成功，下载音频: {}", audioUrl);

        // 4. 下载音频字节
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
        throw new IllegalStateException("未配置 DashScope API Key，请在 application.yaml 设置 dashscope.api-key 或环境变量 DASHSCOPE_API_KEY");
    }
}