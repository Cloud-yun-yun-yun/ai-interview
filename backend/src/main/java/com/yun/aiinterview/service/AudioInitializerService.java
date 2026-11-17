package com.yun.aiinterview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioInitializerService implements ApplicationRunner {

    /** 首题固定开场白文案（全局常量，启动时合成音频缓存） */
    public static final String INTRO =
            "你好，欢迎来到AI语音面试。在正式开始之前，请你先做一段简短的自我介绍，"
                    + "请用两分钟左右的时间重点介绍一下你的项目经历、实习经历，"
                    + "以及简历中提到的重点项目的职责、你的个人贡献与收获。";
    /** 末题固定结束语文案（全局一份，启动时合成音频缓存） */
    public static final String OUTRO =
            "本轮面试到此结束。感谢您的参与，"
                    + "您可以稍后查看面试详情。再见。祝您生活愉快！";
    private static final String INTRO_FILE_KEY = "voices/intro.wav";
    private static final String OUTRO_FILE_KEY = "voices/outro.wav";

    private final QwenTtsService qwenTtsService;
    private final FileStorageService fileStorageService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureIntroAudio();
        ensureOutroAudio();
    }

    public String getIntroFileKey() {
        ensureIntroAudio();
        return INTRO_FILE_KEY;
    }

    public String getOutroFileKey() {
        ensureOutroAudio();
        return OUTRO_FILE_KEY;
    }

    private void ensureIntroAudio() {
        synthesizeVoice(INTRO, INTRO_FILE_KEY);
    }

    private void ensureOutroAudio() {
        synthesizeVoice(OUTRO, OUTRO_FILE_KEY);
    }

    private void synthesizeVoice(String text, String key) {
        if (fileStorageService.fileExists(key)) {
            log.info("语音面试固定音频已存在，直接复用: {}", key);
            return;
        }
        try {
            byte[] audio = qwenTtsService.synthesizeSync(text);
            fileStorageService.uploadAudioTo(audio, key);
            log.info("语音面试固定音频合成并缓存成功: {}", key);
        } catch (Exception e) {
            log.error("语音面试固定音频生成失败: {}", e.getMessage(), e);
        }
    }
}