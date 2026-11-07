<script setup lang="ts">
import {onMounted, ref, onUnmounted} from 'vue'
import { useRouter } from 'vue-router'
import { Document, Collection, Loading } from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {createInterviewWithKb,
  createInterviewWithResume,
  getResumeList,
  getSession,
  type CreateInterviewSessionDTO,
  type CreateKbInterviewSessionDTO,
  getKnowledgeBaseList,
  type ResumeVO,
  type KnowledgeBaseVO,
  type InterviewSessionVO,
  type AsyncTaskStatus
} from "@/api/business.ts";

const router = useRouter()

const CURRENT_SESSION_KEY = 'currentInterviewSessionId'
const POLL_INTERVAL = 2000

interface Resume {
  id: number
  name: string
}
interface Knowledge {
  id: number
  name: string
}

const resumeDto = ref<CreateInterviewSessionDTO>({
  resumeId: 0,
  requestId: ''
})
const kbDto = ref<CreateKbInterviewSessionDTO>({
  kbId: 0,
  requestId: ''
})

const selectedResume = ref('')
const selectedKnowledge = ref('')
const resumeOptions = ref<Resume[]>([])
const knowledgeOptions = ref<Knowledge[]>([])
const loading = ref(false)

let pollingTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    const [resumeRes, kbRes] = await Promise.all([getResumeList(), getKnowledgeBaseList()])
    // 映射简历选项
    if (resumeRes) {
      resumeOptions.value = resumeRes.map((item: ResumeVO) => ({
        id: item.id,
        name: item.name
      }))
    }
    // 映射知识库选项
    if (kbRes) {
      knowledgeOptions.value = kbRes.map((item: KnowledgeBaseVO) => ({
        id: item.id,
        name: item.name
      }))
    }
  } catch (error) {
    console.error('获取素材列表失败', error)
  }
})

onUnmounted(() => {
  stopPolling()
})

// 抽取公共逻辑：根据已选素材创建面试会话，返回 sessionId
const createSession = async (): Promise<string> => {
  const hasResume = !!selectedResume.value;
  const hasKb = !!selectedKnowledge.value;
  const requestId = crypto.randomUUID();
  resumeDto.value.requestId = requestId;
  kbDto.value.requestId = requestId;

  // 同时选择时，让用户选择基于哪个素材面试
  if (hasResume && hasKb) {
    return new Promise<string>((resolve) => {
      ElMessageBox.confirm(
          '您同时选择了简历和知识库，请决定本次面试将基于哪一个进行？',
          '选择面试模式',
          {
            distinguishCancelAndClose: true,
            confirmButtonText: '基于简历面试',
            cancelButtonText: '基于知识库面试',
            type: 'info',
          }
      ).then(async () => {
        resumeDto.value.resumeId = Number(selectedResume.value)
        const result = await createInterviewWithResume(resumeDto.value) as InterviewSessionVO | null
        resolve(result?.sessionId || '')
      }).catch(async (action: string) => {
        if (action === 'cancel') {
          kbDto.value.kbId = Number(selectedKnowledge.value)
          const result = await createInterviewWithKb(kbDto.value) as InterviewSessionVO | null
          resolve(result?.sessionId || '')
        } else {
          resolve('')
        }
      })
    })
  }

  if (hasResume) {
    resumeDto.value.resumeId = Number(selectedResume.value)
    const result = await createInterviewWithResume(resumeDto.value) as InterviewSessionVO | null
    return result?.sessionId || ''
  }

  kbDto.value.kbId = Number(selectedKnowledge.value)
  const result = await createInterviewWithKb(kbDto.value) as InterviewSessionVO | null
  return result?.sessionId || ''
}

const stopPolling = () => {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 轮询面试题目生成状态，COMPLETED 才跳转面试页
const startPolling = (sessionId: string) => {
  stopPolling()
  pollingTimer = setInterval(async () => {
    try {
      const session = await getSession(sessionId) as InterviewSessionVO | null
      const status = session?.generationStatus as AsyncTaskStatus | undefined
      if (status === 'COMPLETED') {
        stopPolling()
        localStorage.setItem(CURRENT_SESSION_KEY, sessionId)
        router.push(`/backend/interview/exam/${sessionId}`)
      } else if (status === 'FAILED') {
        stopPolling()
        loading.value = false
        ElMessage.error('AI 出题失败，请稍后重试')
      }
    } catch (error) {
      console.error('查询面试状态失败:', error)
      stopPolling()
      loading.value = false
      ElMessage.error('查询面试状态失败，请稍后重试')
    }
  }, POLL_INTERVAL)
}

// 开始面试
const handleStart = async () => {
  const hasResume = !!selectedResume.value;
  const hasKb = !!selectedKnowledge.value;

  if (!hasResume && !hasKb) {
    if (resumeOptions.value.length === 0 && knowledgeOptions.value.length === 0) {
      ElMessage.warning('您还没有上传任何简历或知识库，请先去上传！')
    } else {
      ElMessage.warning('请至少选择一项素材开始面试')
    }
    return
  }

  loading.value = true
  try {
    const sessionId = await createSession()
    if (sessionId) {
      localStorage.setItem(CURRENT_SESSION_KEY, sessionId)
      startPolling(sessionId)
    } else {
      loading.value = false
      ElMessage.error('AI 出题失败，请稍后重试')
    }
  } catch (error) {
    loading.value = false
    console.error('创建面试失败:', error)
    ElMessage.error('创建面试会话失败，请稍后重试')
  }
}
</script>

<template>
  <div class="interview-prep-container">
    <!-- Loading 视图 -->
    <div v-if="loading" class="loading-view">
      <el-icon class="loading-icon" :size="56">
        <Loading />
      </el-icon>
      <p class="loading-text">AI 正在为您生成面试题目...</p>
      <p class="loading-sub">请稍候，这可能需要几秒钟</p>
    </div>

    <!-- 准备表单 -->
    <div v-else class="prep-card">
      <!-- 头部标题 -->
      <div class="card-header">
        <h2>文字面试</h2>
        <p class="subtitle">请配置您的面试素材，开始专属挑战</p>
      </div>
      <!-- 表单区域 -->
      <div class="form-area">
        <!-- 简历选择 -->
        <div class="form-item"  v-if="resumeOptions.length > 0">
          <div class="label">
            <el-icon><Document /></el-icon>
            基于简历面试 <span class="optional">(可选)</span>
          </div>
          <el-select
              v-model="selectedResume"
              placeholder="请选择一份已上传的简历（不选则为通用提问）"
              class="custom-select"
              clearable
          >
            <el-option
                v-for="item in resumeOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
            />
          </el-select>
        </div>
        <div v-else class="empty-tip">
          <span>暂无简历，</span>
          <el-link type="primary" @click="$router.push('/backend/resume')">去上传</el-link>
        </div>

        <div class="form-item" v-if="knowledgeOptions.length > 0">
          <div class="label">
            <el-icon><Collection /></el-icon>
            基于知识库面试 <span class="optional">(可选)</span>
          </div>
          <el-select
              v-model="selectedKnowledge"
              placeholder="请选择一个已上传的知识库"
              class="custom-select"
              clearable
          >
            <el-option
                v-for="item in knowledgeOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
            />
          </el-select>
        </div>
        <div v-else class="empty-tip">
          <span>暂无知识库，</span>
          <el-link type="primary" @click="$router.push('/backend/knowledgeBase/upload')">去创建</el-link>
        </div>
      </div>

      <!-- 开始按钮 -->
      <div class="action-footer">
        <el-button
            type="primary"
            class="start-btn"
            size="large"
            @click="handleStart"
        >
          开始面试
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 外层背景，撑满屏幕并居中 */
.interview-prep-container {
  min-height: 80vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f7f9fc;
  padding: 20px;
}

/* 核心卡片 */
.prep-card {
  width: 100%;
  max-width: 540px;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px;
  box-sizing: border-box;
}

/* 头部样式 */
.card-header {
  margin-bottom: 32px;
}

.card-header h2 {
  font-size: 24px;
  color: #1a1a1a;
  margin: 0 0 8px 0;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: #8c92a4;
  margin: 0;
}

/* 表单区域 */
.form-area {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin-bottom: 40px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 标签样式 */
.label {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.label .el-icon {
  margin-right: 6px;
  color: #626aef;
}

.optional {
  color: #a8abb2;
  font-weight: normal;
  margin-left: 4px;
}

/* 下拉框宽度撑满 */
.custom-select {
  width: 100%;
}

/* 修改下拉框内部的默认高度，让它看起来更大气 */
:deep(.el-select__wrapper) {
  padding: 8px 12px;
  border-radius: 10px;
}

/* 底部开始按钮 */
.action-footer {
  width: 100%;
}

.start-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  background-color: #626aef;
  border-color: #626aef;
}

.start-btn:hover {
  background-color: #7b82f3; /* 悬浮稍微变亮 */
  border-color: #7b82f3;
}

/* Loading 视图 */
.loading-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.loading-icon {
  color: #626aef;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 18px;
  color: #333;
  font-weight: 500;
  margin: 0;
}

.loading-sub {
  font-size: 14px;
  color: #999;
  margin: 0;
}
</style>