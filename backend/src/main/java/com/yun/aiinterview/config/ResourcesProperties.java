package com.yun.aiinterview.config;


import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class ResourcesProperties {
    private String resumeAnalysisSystem = "classpath:prompts/resume-analysis-system.md";
    private String resumeAnalysisUser = "classpath:prompts/resume-analysis-user.md";
    private String interviewResumeSystem = "classpath:prompts/interview-question-resume-system.md";
    private String interviewResumeUser = "classpath:prompts/interview-question-resume-user.md";
    private String interviewEvaluationSystem = "classpath:prompts/interview-evaluation-system.md";
    private String interviewEvaluationUser = "classpath:prompts/interview-evaluation-user.md";
    private String questionGenSystem = "classpath:prompts/knowledgebase-question-generation-system.md";
    private String questionGenUser = "classpath:prompts/knowledgebase-question-generation-user.md";
}
