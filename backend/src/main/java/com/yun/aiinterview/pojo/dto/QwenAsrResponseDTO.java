package com.yun.aiinterview.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DashScope Qwen-Audio ASR 语音识别响应体（阻塞式多模态生成）。
 * 识别文本位于 output.sentence.text。
 */
@Data
@NoArgsConstructor
public class QwenAsrResponseDTO {

    private Output output;

    private String request_id;

    @Data
    @NoArgsConstructor
    public static class Output {
        private Sentence sentence;
        private String finish_reason;
    }

    @Data
    @NoArgsConstructor
    public static class Sentence {
        private Integer sentence_id;
        private Integer begin_time;
        private Integer end_time;
        private String text;
        private Integer channel_id;
        private Boolean sentence_end;
    }
}