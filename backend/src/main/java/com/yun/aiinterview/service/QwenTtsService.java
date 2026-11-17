package com.yun.aiinterview.service;

import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisResult;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.yun.aiinterview.config.VoiceInterviewProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * DashScope 语音合成（TTS）工具类。
 * 基于官方 HttpSpeechSynthesizer SDK 做流式合成：边合成边把音频分片推给回调。
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

    /** 出题音频是否采用后端非流式自动合成（sync 模式）；false 表示走前端 /stream/turn 流式 */
    public boolean isSyncMode() {
        return !"stream".equalsIgnoreCase(properties.getTts().getMode());
    }

    /**
     * 流式语音合成：边合成边回调音频分片。
     *
     * @param text      待合成的文本
     * @param chunkSink 音频分片回调，收到即为可播放的音频字节
     */
    public void streamSynthesize(String text, Consumer<byte[]> chunkSink) {
        streamSynthesize(text, null, chunkSink);
    }

    /**
     * 流式语音合成（官方 SDK）。
     * eventSink 参数保留以兼容上层调用；http_tts SDK 不暴露句子级信号，暂不回调 eventSink。
     *
     * @param text      待合成的文本
     * @param eventSink 句子级信号回调（SDK 未提供，保留占位）
     * @param chunkSink 音频分片回调
     */
    public void streamSynthesize(String text, Consumer<String> eventSink, Consumer<byte[]> chunkSink) {
        String apiKey = resolveApiKey();
        HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                .model(model())
                .text(text)
                .voice(voice())
                .format(format())
                .sampleRate(sampleRate())
                .apiKey(apiKey)
                .build();

        log.info("调用流式 TTS(SDK), model={}, voice={}, format={}, text={}",
                model(), voice(), format(), text);

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
     * 用流式 SDK 合成整段音频：内部把收到的全部分片拼接成完整字节数组返回。
     * 供一次性合成（如固定的开场白 / 结束语）使用。
     *
     * @param text 待合成的文本
     * @return 完整音频字节（格式由配置决定）
     */
    public byte[] synthesizeFull(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        streamSynthesize(text, out::writeBytes);
        return out.toByteArray();
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
        HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                .model(model())
                .text(text)
                .voice(voice())
                .format(format())
                .sampleRate(sampleRate())
                .apiKey(apiKey)
                .build();

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