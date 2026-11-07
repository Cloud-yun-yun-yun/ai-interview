package com.yun.aiinterview.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRetryTask {

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryMessages() {

    }


}