package com.yun.aiinterview;

import com.yun.aiinterview.entity.InterviewQuestion;
import com.yun.aiinterview.entity.InterviewSession;
import com.yun.aiinterview.mapper.InterviewQuestionMapper;
import com.yun.aiinterview.mapper.InterviewSessionMapper;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@SpringBootTest
class AiInterviewApplicationTests {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

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