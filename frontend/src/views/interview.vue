<script setup lang="ts">
import {onMounted, ref} from 'vue'
import { useRouter } from 'vue-router'
import { Document, Collection, Loading } from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {createInterviewWithKb, createInterviewWithResume, getResumeList, getKnowledgeBaseList,
  type ResumeVO, type KnowledgeBaseVO} from "@/api/business.ts";

const router = useRouter()

interface Resume {
  id: number
  name: string
}
interface Knowledge {
  id: number
  name: string
}
const selectedResume = ref('')
const selectedKnowledge = ref('')
const resumeOptions = ref<Resume[]>([])
const knowledgeOptions = ref<Knowledge[]>([])
const loading = ref(false)

// 开始面试
const handleStart = async () => {
  const resumeId = selectedResume.value ? Number(selectedResume.value) : null
  const kbId = selectedKnowledge.value ? Number(selectedKnowledge.value) : null

  if (!resumeId && !kbId) {
    ElMessage.warning('请选择一种面试素材')
    return
  }
  loading.value = true
  try {
    const requestId = crypto.randomUUID()
    const session = resumeId
        ? await createInterviewWithResume({ resumeId, requestId })
        : await createInterviewWithKb({ kbId: kbId!, requestId })

    if (!session?.sessionId) throw new Error('no sessionId')
    const sessionId = session.sessionId
    router.push({ name: 'interviewExam', params: { sessionId } })
  }catch (e) {
    loading.value = false
    ElMessage.error('创建面试会话失败，请稍后重试')
  }
}
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

    <div v-else class="prep-card">
      <div class="card-header">
        <h2>文字面试</h2>
        <p class="subtitle">请配置您的面试素材，开始专属挑战</p>
      </div>
      <div class="form-area">
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
              :disabled="!!selectedKnowledge"
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
              :disabled="!!selectedResume"
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
.interview-prep-container {
  min-height: 80vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f7f9fc;
  padding: 20px;
}
.prep-card {
  width: 100%;
  max-width: 540px;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px;
  box-sizing: border-box;
}
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

.custom-select {
  width: 100%;
}
:deep(.el-select__wrapper) {
  padding: 8px 12px;
  border-radius: 10px;
}
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
  background-color: #7b82f3;
  border-color: #7b82f3;
}
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