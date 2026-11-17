package com.yun.aiinterview;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisResult;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同一段文本（开场白）下，非流式 vs 流式（收集完毕）耗时对比测试。
 *
 * 前置条件：在运行前设置环境变量 DASHSCOPE_API_KEY（与 application.yaml 中 voice.interview.api-key 一致）。
 *
 * 运行方式：在 IDE 中直接运行本类的 main 方法，或在项目根目录执行：
 *   mvn -q test-compile exec:java -Dexec.mainClass=com.yun.aiinterview.QwenTtsStreamVsSyncBenchmark
 */
public class QwenTtsStreamVsSyncBenchmark {

    /** 与 AudioInitializerService.INTRO 相同的文本 */
    static final String TEXT =
            "你好，欢迎来到AI语音面试。在正式开始之前，请你先做一段简短的自我介绍，"
            + "请用两分钟左右的时间重点介绍一下你的项目经历、实习经历，"
            + "以及简历中提到的重点项目的职责、你的个人贡献与收获。";

    static final String MODEL = "qwen-audio-3.1-tts-flash";
    static final String VOICE = "longanhuan_v3.1";
    static final String FORMAT = "wav";
    static final int SAMPLE_RATE = 24000;

    static {
        Constants.baseHttpApiUrl = "https://maas.qianwenaiapi.com/api/v1";
    }

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请在运行前设置环境变量 DASHSCOPE_API_KEY");
            System.exit(1);
        }

        System.out.println("==== 测试文本 ====");
        System.out.println(TEXT);
        System.out.println("==== 预热 1 次（避免首次建连影响）====");
        syncCallReturnAudio(apiKey);

        // 各测 3 次取平均，减小抖动
        double syncAvg = 0, streamAvg = 0;
        int rounds = 3;
        for (int i = 1; i <= rounds; i++) {
            long syncMs = syncCallReturnAudio(apiKey);
            long streamMs = streamCollectAll(apiKey);
            System.out.printf("[第%d次] 非流式=%dms  流式(收集完毕)=%dms  差=%dms\n",
                    i, syncMs, streamMs, syncMs - streamMs);
            syncAvg += syncMs;
            streamAvg += streamMs;
        }
        syncAvg /= rounds;
        streamAvg /= rounds;
        System.out.printf("\n==== 平均 ==== 非流式=%.0fms  流式=%.0fms  快 %.1f 倍\n",
                syncAvg, streamAvg, syncAvg / streamAvg);

        // 额外对比：qwen3-tts-flash（纯语音模型，MultiModalConversation）
        long qwen3Avg = 0;
        for (int i = 1; i <= rounds; i++) {
            long qwen3Ms = qwen3TtsFlash(apiKey);
            qwen3Avg += qwen3Ms;
            System.out.printf("[第%d次] qwen3-tts-flash=%dms\n", i, qwen3Ms);
        }
        qwen3Avg /= rounds;
        System.out.printf("\n==== 平均 ==== qwen3-tts-flash=%.0fms   vs  流式(audio3.1)=%.0fms\n",
                (double) qwen3Avg, streamAvg);
    }

    /** qwen3-tts-flash：多模态会话接口，返回音频 URL，需另行下载，此处计入下载时间 */
    static long qwen3TtsFlash(String apiKey)
            throws ApiException, NoApiKeyException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model("qwen3-tts-flash")
                .apiKey(apiKey)
                .text(TEXT)
                .voice(AudioParameters.Voice.CHERRY)
                .build();
        long start = System.currentTimeMillis();
        MultiModalConversationResult result = conv.call(param);
        long cost = System.currentTimeMillis() - start;
        String url = null;
        if (result.getOutput() != null && result.getOutput().getAudio() != null) {
            url = result.getOutput().getAudio().getUrl();
        }
        System.out.println("[qwen3-tts-flash] 返回音频URL=" + url + ", 合成耗时=" + cost + "ms");
        return cost;
    }

    /** 非流式：阻塞等待整段合成完成返回 */
    static long syncCallReturnAudio(String apiKey)
            throws ApiException, NoApiKeyException, InputRequiredException {
        HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
        HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                .model(MODEL).text(TEXT).voice(VOICE).format(FORMAT).sampleRate(SAMPLE_RATE)
                .apiKey(apiKey).build();
        long start = System.currentTimeMillis();
        ByteBuffer audioData = synthesizer.callAndReturnAudio(param);
        long cost = System.currentTimeMillis() - start;
        int size = (audioData != null) ? audioData.remaining() : 0;
        System.out.println("[非流式] 完成, 音频字节=" + size + ", 耗时=" + cost + "ms");
        return cost;
    }

    /** 流式：不做真流式播放，用 streamCall 把所有分片收集完毕后整体计时 */
    static long streamCollectAll(String apiKey)
            throws InterruptedException, ApiException, NoApiKeyException, InputRequiredException {
        HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
        HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                .model(MODEL).text(TEXT).voice(VOICE).format(FORMAT).sampleRate(SAMPLE_RATE)
                .apiKey(apiKey).build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        AtomicLong totalBytes = new AtomicLong();
        long start = System.currentTimeMillis();

        synthesizer.streamCall(param, new ResultCallback<HttpSpeechSynthesisResult>() {
            @Override
            public void onEvent(HttpSpeechSynthesisResult result) {
                if (result != null && result.hasAudioData()) {
                    totalBytes.addAndGet(result.getAudioData().length);
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
        long cost = System.currentTimeMillis() - start;
        Exception err = errorRef.get();
        if (err != null) {
            System.err.println("[流式] 失败: " + err.getMessage());
        } else {
            System.out.println("[流式] 完成, 音频字节=" + totalBytes.get() + ", 耗时=" + cost + "ms");
        }
        return cost;
    }
}