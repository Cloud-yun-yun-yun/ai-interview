package com.yun.aiinterview.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.pojo.vo.KnowledgeBaseVO;
import com.yun.aiinterview.pojo.vo.QuestionGenStatusVO;
import com.yun.aiinterview.result.Result;
import com.yun.aiinterview.service.KbQuestionGenService;
import com.yun.aiinterview.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/kbs")
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBasesController {

    private final KbQuestionGenService kbQuestionGenService;
    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/generate")
    public Result<QuestionGenStatusVO> generateQuestions(@RequestParam Long kbId) {
        return Result.success(kbQuestionGenService.submitGenerationTask(kbId));
    }

    @PostMapping("/upload")
    public Result<Long> uploadFile(@RequestParam("file") MultipartFile file) {
        KnowledgeBase kb = knowledgeBaseService.upload(file);
        return Result.success(kb.getId());
    }

    @GetMapping("/list")
    public Result<List<KnowledgeBaseVO>> listKnowledgeBases() {
        return Result.success(knowledgeBaseService.listKnowledgeBases());
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return Result.success();
    }

}