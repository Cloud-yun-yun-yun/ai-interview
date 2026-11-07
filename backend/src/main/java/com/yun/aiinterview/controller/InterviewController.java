package com.yun.aiinterview.controller;

import lombok.RequiredArgsConstructor;
import com.yun.aiinterview.annotation.RateLimit;
import com.yun.aiinterview.context.BaseContext;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.pojo.dto.CreateInterviewSessionDTO;
import com.yun.aiinterview.pojo.dto.CreateKbInterviewSessionDTO;
import com.yun.aiinterview.pojo.dto.QuestionAnswer;
import com.yun.aiinterview.pojo.vo.InterviewEvaluationVO;
import com.yun.aiinterview.pojo.vo.InterviewSessionVO;
import com.yun.aiinterview.result.Result;
import com.yun.aiinterview.service.InterviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;

    @PostMapping("/session/create")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
    public Result<InterviewSessionVO> createInterview(@RequestBody CreateInterviewSessionDTO dto) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(interviewService.createInterview(dto, userId));
    }

    @PostMapping("/session/kbcreate")
    public Result<InterviewSessionVO> createInterview(@RequestBody CreateKbInterviewSessionDTO dto) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(interviewService.createInterview(dto,userId));
    }

    @PostMapping("/answers/submit")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
    public Result<String> submitAnswer(@RequestBody QuestionAnswer qa) {
        if (qa == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "qa is null");
        }
        Long userId = BaseContext.getCurrentId();
        interviewService.submit(qa, userId);
        return Result.success("success");
    }

    @GetMapping("/evaluation")
    public Result<InterviewEvaluationVO> getEvaluation(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话Id不存在");
        }
        Long userId = BaseContext.getCurrentId();
        return Result.success(interviewService.getEvaluation(sessionId, userId));
    }

    @GetMapping("/session/detail")
    public Result<InterviewSessionVO> getSession(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话Id不存在");
        }
        Long userId = BaseContext.getCurrentId();
        return Result.success(interviewService.getSessionDetail(sessionId, userId));
    }

    @DeleteMapping("/session/remove")
    public Result<String> removeSession(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话Id不存在");
        }
        Long userId = BaseContext.getCurrentId();
        interviewService.removeSession(sessionId, userId);
        return Result.success();
    }

    @PutMapping("/answer/update")
    public Result<String> updateAnswer(@RequestBody QuestionAnswer qa){
        if (qa == null || qa.sessionId() == null || qa.currentIndex() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不完整");
        }
        Long userId = BaseContext.getCurrentId();
        interviewService.updateAnswer(qa, userId);
        return Result.success("修改成功");
    }

}