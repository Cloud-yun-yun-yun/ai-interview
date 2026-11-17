package com.yun.aiinterview.controller;

import com.yun.aiinterview.context.BaseContext;
import com.yun.aiinterview.entity.VoiceInterviewSession;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.pojo.dto.VoiceInterviewSubmitDTO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewSessionVO;
import com.yun.aiinterview.pojo.vo.VoiceInterviewTurnVO;
import com.yun.aiinterview.result.Result;
import com.yun.aiinterview.service.VoiceInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/interview/voice")
@RequiredArgsConstructor
public class VoiceInterviewController {
    private final VoiceInterviewService voiceInterviewService;

    @PostMapping("/session/create")
    public Result<VoiceInterviewSessionVO> createInterview(
            @RequestParam("resumeId") Long resumeId){
        Long userId = BaseContext.getCurrentId();
        return Result.success(voiceInterviewService.createSession(resumeId, userId));
    }

    @GetMapping("/current/turn")
    public Result<VoiceInterviewTurnVO> getCurrentTurn(@RequestParam("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话Id不存在");
        }
        Long userId = BaseContext.getCurrentId();
        return Result.success(voiceInterviewService.getCurrentTurn(userId, sessionId));
    }

    @GetMapping(value = "/stream/turn", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTurn(@RequestParam("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话Id不存在");
        }
        Long userId = BaseContext.getCurrentId();
        return voiceInterviewService.streamTurn(userId, sessionId);
    }

    @GetMapping("/current/session")
    public Result<VoiceInterviewSessionVO> getCurrentSession(@RequestParam("sessionId") String sessionId) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(voiceInterviewService.getSession(userId,sessionId));
    }

    @GetMapping("/session/list")
    public Result<List<VoiceInterviewSessionVO>> getSessionList() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(voiceInterviewService.getSessionList(userId));
    }

    @PostMapping("/submit/turn")
    public Result<VoiceInterviewSessionVO> submit(@RequestBody VoiceInterviewSubmitDTO dto){
        Long userId = BaseContext.getCurrentId();
        return Result.success(voiceInterviewService.submit(userId,dto));
    }

    @PostMapping("/audio/upload")
    public Result<String> uploadAudio(@RequestParam("file") MultipartFile file,
                                      @RequestParam("sessionId") String sessionId){
        if(file.isEmpty()){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"文件不可为空");
        }
        if (!isSupportedAudio(file)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"文件类型必须为音频格式(mp3/wav/webm/m4a)");
        }
        try {
            Long userId = BaseContext.getCurrentId();
            String key = voiceInterviewService.uploadAudio(userId, sessionId, file.getBytes(), file.getOriginalFilename());
            return Result.success(key);
        }catch(IOException e){
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage());
        }
    }

    @GetMapping("/audio/download")
    public ResponseEntity<byte[]> downloadAudio(@RequestParam("fileKey") String fileKey) {
        byte[] data = voiceInterviewService.downloadAudio(fileKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"voice.mp3\"")
                .body(data);
    }

    @DeleteMapping("/session/remove")
    public Result<String> deleteSession(@RequestParam("sessionId")String sessionId){
        Long userId = BaseContext.getCurrentId();
        voiceInterviewService.removeSession(userId,sessionId);
        return Result.success();
    }

    private boolean isSupportedAudio(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".webm")
                    || lower.endsWith(".m4a") || lower.endsWith(".ogg")) {
                return true;
            }
        }
        String ct = file.getContentType();
        return ct != null && ct.startsWith("audio/");
    }
}