package com.yun.aiinterview;

import com.yun.aiinterview.entity.InterviewQuestion;
import com.yun.aiinterview.entity.InterviewSession;
import com.yun.aiinterview.mapper.InterviewQuestionMapper;
import com.yun.aiinterview.mapper.InterviewSessionMapper;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.service.DashscopeTtsService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@SpringBootTest
class AiInterviewApplicationTests {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private DashscopeTtsService dashscopeTtsService;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    /**
     * TTS 冒烟测试：把一段文本转成 wav，落到桌面 audio-tts-smoke.wav，双击即可播放验证。
     * 运行后可 Ctrl 点击控制台里输出的文件路径打开。
     */
    @Test
    @Disabled("需要真实 DASHSCOPE_API_KEY，保留为手动冒烟测试")
    void testTtsSmoke() throws IOException {
        String text = "你好，欢迎来到AI语音面试平台，请听题。请介绍一下你自己。";
        byte[] audio = dashscopeTtsService.synthesize(text);
        Path out = Paths.get(System.getProperty("user.home"), "Desktop", "audio-tts-smoke.wav").toAbsolutePath();
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