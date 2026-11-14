package com.yun.aiinterview;

import com.yun.aiinterview.service.QwenTtsService;
import com.yun.aiinterview.service.QwenAsrService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@SpringBootTest
class AiInterviewApplicationTests {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private QwenTtsService qwenTtsService;

    @Autowired
    private QwenAsrService qwenAsrService;

    @Test
    void testAsr() throws IOException {
        byte[] audio;
        try (var in = getClass().getClassLoader().getResourceAsStream("")) {
            if (in == null) {
                throw new IllegalStateException("resources 下找不到音频 ");
            }
            audio = in.readAllBytes();
        }
        System.out.println("读入音频大小=" + audio.length + " bytes");
        String text = qwenAsrService.transcribe(audio, "mp3");
        // 用 UTF-8 落盘验证编码,绕开控制台 GBK 乱码干扰
        Path out = Paths.get("D:\\Output\\JavaCode\\ai-interview\\backend\\out\\asr-result.txt").toAbsolutePath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("ASR 识别结果(utf8文件): " + out);
        System.out.println("ASR 文本长度=" + text.length() + ", 内容后附");
        if (text.isBlank()) {
            throw new IllegalStateException("ASR 返回空文本");
        }
    }

    /**
     * TTS 冒烟测试：把一段文本转成 mp3，落到桌面 audio-tts-smoke.mp3，双击即可播放验证。
     * 运行后可 Ctrl 点击控制台里输出的文件路径打开。
     */
    @Test
    @Disabled("需要真实 DASHSCOPE_API_KEY，保留为手动冒烟测试")
    void testTtsSmoke() throws IOException {
        String text = "你好，欢迎来到AI语音面试平台，请听题。请介绍一下你自己。";
        byte[] audio = qwenTtsService.synthesize(text);
        Path out = Paths.get(System.getProperty("user.home"), "Desktop", "audio-tts-smoke.mp3").toAbsolutePath();
        Files.write(out, audio);
        System.out.println("TTS 冒烟测试完成，音频已保存到: " + out + "，大小=" + audio.length + " 字节");
        if (audio.length == 0) {
            throw new IllegalStateException("TTS 返回空音频，请检查 key 或配置");
        }
    }

    @Test
    void testChat() {
        String answer = chatClient
                .prompt()
                .user("你是谁")
                .call()
                .content();
        System.out.println("大模型回答：" + answer);
    }
}