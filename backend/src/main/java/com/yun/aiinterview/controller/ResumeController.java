package com.yun.aiinterview.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.annotation.RateLimit;
import com.yun.aiinterview.context.BaseContext;
import com.yun.aiinterview.entity.ResumeAnalysisResponse;
import com.yun.aiinterview.pojo.vo.ResumeAnalysisVO;
import com.yun.aiinterview.pojo.vo.ResumeVO;
import com.yun.aiinterview.result.Result;
import com.yun.aiinterview.service.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/resume")
@Slf4j
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) {
        Long userId = BaseContext.getCurrentId();
        resumeService.uploadAndParse(userId, file);
        return Result.success("上传并解析成功");

    }

    @GetMapping("/analysis")
    public Result<ResumeAnalysisVO> analysisResume(@RequestParam("resumeId") Long resumeId) {
        Long userId = BaseContext.getCurrentId();
        ResumeAnalysisVO vo = resumeService.analysisResume(resumeId, userId);
        return Result.success(vo);
    }

    @GetMapping("/analysis/result")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
    public Result<ResumeAnalysisResponse> getAnalysisResponse(@RequestParam("resumeId") Long resumeId) {
        Long userId = BaseContext.getCurrentId();
        ResumeAnalysisResponse response = resumeService.getAnalysisResponse(resumeId, userId);
        return Result.success(response);
    }

    @GetMapping("/list")
    public Result<List<ResumeVO>> listResumes() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(resumeService.listResumes(userId));
    }

    @DeleteMapping("/remove")
    public Result<Void> deleteResume(@RequestParam("resumeId") Long resumeId) {
        Long userId = BaseContext.getCurrentId();
        resumeService.deleteResume(resumeId, userId);
        return Result.success();
    }
}