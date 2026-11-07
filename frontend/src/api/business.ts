import service from '@/utils/request'

export function uploadResume(file:File){
    const formData = new FormData()
    formData.append('file', file)
    return service.post('/api/resume/upload',formData,{
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}
export function uploadKnowledgeBase(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return service.post('/api/kbs/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}

export type AsyncTaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ResumeVO {
    name: string;
    id: number;
    userId: number;
    textLength: number;
    interviewStatus: string;
    asyncTaskStatus: AsyncTaskStatus;
}
export async function getResumeList(): Promise<ResumeVO[]> {
    return await service.get('/api/resume/list') as any
}

export function analysisResume(resumeId: number) {
    return service.get('/api/resume/analysis', { params: { resumeId } }) as any
}

export function deleteResume(resumeId: number) {
    return service.delete('/api/resume/remove', { params: { resumeId } }) as any
}

// 建议项的内部结构
export interface Suggestion {
    category: string;
    priority: string;
    issue: string;
    recommendation: string;
}

export interface ResumeAnalysisResponse {
    id: number;
    resumeId: number;
    userId: number;
    overallScore: number;
    projectScore: number;
    skillMatchScore: number;
    contentScore: number;
    structureScore: number;
    expressionScore: number;
    summary: string;
    allowReparse: boolean;
    suggestions: Suggestion[];
}
export function getResumeDetail(resumeId: number) {
    return service.get<ResumeAnalysisResponse>('/api/resume/analysis/result', { params: { resumeId } }) as any
}

export function getResumeStatus(resumeId: number) {
    return service.get<AsyncTaskStatus>('/api/resume/status', { params: { resumeId } }) as any
}

export interface ResumeStatusVO {
    resumeId: number
    status: AsyncTaskStatus
}

export function getResumeStatusBatch(resumeIds: number[]) {
    return service.get<ResumeStatusVO[]>('/api/resume/status/batch', {
        params: { resumeIds: resumeIds.join(',') }
    }) as any
}

export type InterviewSessionStatus = 'CREATED' | 'SUBMITTED' | 'EVALUATED';

export interface InterviewQuestionVO {
    questionIndex: number;
    questionContent: string;
    topicSummary: string;
    type: string;
    userAnswer?: string | null;
}

export interface InterviewSessionVO {
    sessionId: string;
    userId: number;
    currentIndex: number;
    status: InterviewSessionStatus;
    totalQuestions: number;
    questions: InterviewQuestionVO[];
}

export interface CreateInterviewSessionDTO {
    resumeId: number;
    requestId: string;
}

export interface CreateKbInterviewSessionDTO {
    kbId: number;
    requestId: string;
}

export function createInterviewWithResume(dto: CreateInterviewSessionDTO) {
    return service.post<InterviewSessionVO>('/api/interview/session/create', dto) as any
}

export function createInterviewWithKb(dto: CreateKbInterviewSessionDTO) {
    return service.post<InterviewSessionVO>('/api/interview/session/kbcreate', dto) as any
}

export type QuestionGenStatus = 'NONE' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface KnowledgeBaseVO {
    id: number;
    name: string;
    fileSize: string;
    uploadTime: string;
    vectorStatus: AsyncTaskStatus;
    questionGenStatus: QuestionGenStatus;
}
export function getKnowledgeBaseList() {
    return service.get<KnowledgeBaseVO[]>('/api/kbs/list') as any
}