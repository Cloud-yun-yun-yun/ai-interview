package com.yun.aiinterview;

import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class AiInterviewApplicationTests {

    @Autowired
    private ChatClient chatClient;

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