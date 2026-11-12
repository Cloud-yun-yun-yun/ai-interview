package com.yun.aiinterview.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DashScope TTS 语音合成请求体。
 */
@Data
@NoArgsConstructor
public class TtsRequestDTO {

    /** 模型名称，如 qwen-audio-3.1-tts-next */
    private String model;

    /** 合成输入 */
    private Input input;

    @Data
    @NoArgsConstructor
    public static class Input {
        /** 待合成的文本 */
        private String text_prompt;
        /** 音频格式：wav / mp3 / pcm */
        private String format;
        /** 采样率 */
        private Integer sample_rate;
        /** 声道数（仅 wav/pcm 需要，mp3 不传） */
        private Integer channels;
    }

    public static TtsRequestDTO of(String model, String text, String format, Integer sampleRate, Integer channels) {
        TtsRequestDTO dto = new TtsRequestDTO();
        dto.setModel(model);
        Input input = new Input();
        input.setText_prompt(text);
        input.setFormat(format);
        input.setSample_rate(sampleRate);
        if (channels != null) {
            input.setChannels(channels);
        }
        dto.setInput(input);
        return dto;
    }
}