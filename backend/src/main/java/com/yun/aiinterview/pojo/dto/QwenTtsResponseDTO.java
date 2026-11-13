package com.yun.aiinterview.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DashScope TTS 语音合成响应体。
 * 形如：{"output":{"audio":{"url":"...","duration":1.32}},"request_id":"..."}
 */
@Data
@NoArgsConstructor
public class QwenTtsResponseDTO {

    private Output output;

    private String request_id;

    @Data
    @NoArgsConstructor
    public static class Output {
        private Audio audio;
        private String finish_reason;
    }

    @Data
    @NoArgsConstructor
    public static class Audio {
        /** 生成的音频下载地址 */
        private String url;
        private String data;
        private String id;
        private Double duration;
    }
}