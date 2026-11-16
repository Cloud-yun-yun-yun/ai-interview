package com.yun.aiinterview.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * DashScope Qwen-Audio ASR 语音识别请求体（多模态原生协议，同步阻塞模式）。
 * 音频以 base64 Data URL 内嵌在 input_audio，无需公网 URL。
 */
@Data
@NoArgsConstructor
public class QwenAsrRequestDTO {

    /** 模型名称 */
    private String model;

    /** 输入 */
    private Input input;

    /** 识别参数 */
    private Parameters parameters;

    public static QwenAsrRequestDTO of(String model, String dataUri, String format, Integer sampleRate) {
        QwenAsrRequestDTO dto = new QwenAsrRequestDTO();
        dto.setModel(model);

        AudioBlock audioBlock = new AudioBlock();
        audioBlock.setData(dataUri);

        AudioContent audioContent = new AudioContent();
        audioContent.setType("input_audio");
        audioContent.setInput_audio(audioBlock);

        UserMessage user = new UserMessage("user");
        user.setContent(Collections.singletonList(audioContent));

        Input input = new Input();
        input.setMessages(Collections.singletonList(user));
        dto.setInput(input);

        Parameters params = new Parameters();
        params.setFormat(format);
        if (sampleRate != null) {
            params.setSample_rate(sampleRate);
        }
        dto.setParameters(params);
        return dto;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameters {
        /** 音频格式：wav / mp3 / pcm 等 */
        private String format;
        /** 采样率，如 16000 */
        private Integer sample_rate;
    }

    @Data
    @NoArgsConstructor
    public static class Input {
        private List<UserMessage> messages;
    }

    @Data
    @NoArgsConstructor
    public static class UserMessage {
        private String role;
        private List<AudioContent> content;

        public UserMessage(String role) {
            this.role = role;
        }
    }

    @Data
    @NoArgsConstructor
    public static class AudioContent {
        private String type;
        private AudioBlock input_audio;
    }

    @Data
    @NoArgsConstructor
    public static class AudioBlock {
        /** base64 Data URL, 形如 data:audio/mpeg;base64,xxxx */
        private String data;
    }
}