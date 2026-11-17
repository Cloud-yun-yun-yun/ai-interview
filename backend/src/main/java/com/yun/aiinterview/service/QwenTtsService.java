package com.yun.aiinterview.service;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yun.aiinterview.config.VoiceInterviewProperties;
import com.yun.aiinterview.pojo.dto.QwenTtsRequestDTO;
import com.yun.aiinterview.pojo.dto.QwenTtsResponseDTO;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisResult;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

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

    // ---------- 流式 TTS（硬编码，先用于验证 qwen-audio-3.1-tts-flash） ----------
    // 当前 tts-next 不支持流式，临时硬编码可流式的模型与参数，不走配置文件。
    private static final String STREAM_BASE_URL = "https://maas.qianwenaiapi.com/api/v1/services/audio/tts/SpeechSynthesizer";
    private static final String STREAM_MODEL = "qwen-audio-3.1-tts-flash";
    private static final String STREAM_VOICE = "longanhuan_v3.1";
    private static final String STREAM_FORMAT = "wav";
    private static final int STREAM_SAMPLE_RATE = 24000;

    static {
        // MaaS 兼容端点：SDK 默认走 dashscope.aliyuncs.com，这里切到 qianwenaiapi 网关
        Constants.baseHttpApiUrl = "https://maas.qianwenaiapi.com/api/v1";
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式语音合成。
     * <p>DashScope qwen-audio-tts-flash 的 SSE 流中，每条 {@code output.type=sentence-synthesis}
     * 的事件都会在 {@code output.audio.data} 里携带一段 base64 的 WAV 音频分片。
     * 本方法逐行读取这条流，把解码后的音频字节立即通过 chunkSink 回调上层（可实时推给前端播放），
     * 直到流结束。
     *
     * @param text      待合成的文本
     * @param chunkSink 音频分片回调，收到即为可播放的 WAV 字节
     * @throws IllegalStateException 流式合成失败或 API Key 未配置
     */
    public void streamSynthesizeLegacy(String text, Consumer<byte[]> chunkSink) {
        streamSynthesizeLegacy(text, null, chunkSink);
    }

    /**
     * 流式语音合成（可感知句子级进度信号）。
     */
    public void streamSynthesizeLegacy(String text, Consumer<String> eventSink, Consumer<byte[]> chunkSink) {
        String apiKey = resolveApiKey();
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("text", text);
        input.put("voice", STREAM_VOICE);
        input.put("format", STREAM_FORMAT);
        input.put("sample_rate", STREAM_SAMPLE_RATE);
        body.put("model", STREAM_MODEL);
        body.put("input", input);

        log.info("调用流式 TTS, model={}, voice={}, format={}, text={}",
                STREAM_MODEL, STREAM_VOICE, STREAM_FORMAT, text);

        try {
            restClient.post()
                    .uri(STREAM_BASE_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-DashScope-SSE", "enable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new IllegalStateException("流式 TTS 请求失败，HTTP "
                                    + response.getStatusCode().value());
                        }
                        try {
                            parseSseStream(response.getBody(), eventSink, chunkSink);
                        } catch (IOException e) {
                            throw new IllegalStateException("流式 TTS 读取异常: " + e.getMessage(), e);
                        }
                        return null;
                    });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("流式 TTS 请求异常: " + e.getMessage(), e);
        }
    }

    // ---------- 流式 TTS（官方 SDK，上层无需改动） ----------

    /**
     * 流式语音合成（官方 SDK）：边合成边回调音频分片。
     *
     * @param text      待合成的文本
     * @param chunkSink 音频分片回调
     */
    public void streamSynthesize(String text, Consumer<byte[]> chunkSink) {
        streamSynthesize(text, null, chunkSink);
    }

    /**
     * 流式语音合成（官方 SDK）。eventSink 参数保留以兼容上层调用；
     * http_tts SDK 不暴露句子级信号，暂不回调 eventSink。
     */
    public void streamSynthesize(String text, Consumer<String> eventSink, Consumer<byte[]> chunkSink) {
        String apiKey = resolveApiKey();
        HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                .model(STREAM_MODEL)
                .text(text)
                .voice(STREAM_VOICE)
                .format(STREAM_FORMAT)
                .sampleRate(STREAM_SAMPLE_RATE)
                .apiKey(apiKey)
                .build();

        log.info("调用流式 TTS(SDK), model={}, voice={}, format={}, text={}",
                STREAM_MODEL, STREAM_VOICE, STREAM_FORMAT, text);

        HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        try {
            synthesizer.streamCall(param, new ResultCallback<HttpSpeechSynthesisResult>() {
                @Override
                public void onEvent(HttpSpeechSynthesisResult result) {
                    if (result != null && result.hasAudioData()) {
                        chunkSink.accept(result.getAudioData());
                    }
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    errorRef.set(e);
                    latch.countDown();
                }
            });
            latch.await();
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            throw new IllegalStateException("流式 TTS 请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("流式 TTS 被中断", e);
        }

        Exception err = errorRef.get();
        if (err != null) {
            throw new IllegalStateException("流式 TTS 失败: " + err.getMessage(), err);
        }
    }

    /**
     * 逐行解析 SSE 流，把事件类型透出（可选），并把 sentence-synthesis 的音频 base64 解码后回调。
     */
    private void parseSseStream(java.io.InputStream body, Consumer<String> eventSink,
                                Consumer<byte[]> chunkSink) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                try {
                    JsonNode output = objectMapper.readTree(payload).path("output");
                    // 句子级信号（sentence-begin / sentence-end）
                    if (eventSink != null) {
                        String type = output.path("type").asText(null);
                        if (type != null) {
                            eventSink.accept(type);
                        }
                    }
                    // 音频分片：type=sentence-synthesis 时 audio.data 为载体 base64
                    JsonNode audioData = output.path("audio").path("data");
                    if (audioData.isTextual() && !audioData.asText().isEmpty()) {
                        byte[] chunk = Base64.getDecoder().decode(audioData.asText());
                        if (chunk.length > 0) {
                            chunkSink.accept(chunk);
                        }
                    }
                } catch (Exception e) {
                    log.warn("忽略无法解析的 SSE 行: ", e);
                }
            }
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