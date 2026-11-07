<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, Promotion, User } from "@element-plus/icons-vue"
import {
  getSession,
  getSessionQuestions,
  submitAnswer,
  type InterviewSessionVO,
  type InterviewQuestionVO
} from "@/api/business"
import { ElMessage } from "element-plus"
//todo 重构页面布局，改善面试题目展示方式
const route = useRoute()
const router = useRouter()

const sessionId = String(route.params.sessionId || '')

const session = ref<InterviewSessionVO | null>(null)
const questions = ref<InterviewQuestionVO[]>([])
const loading = ref(true)
const submitting = ref(false)
const currentIndex = ref(0)
const answerText = ref('')

const currentQuestion = computed<InterviewQuestionVO | null>(() => {
  return questions.value.find(q => q.questionIndex === currentIndex.value) || null
})

const progressPercent = computed(() => {
  if (questions.value.length === 0) return 0
  return Math.round((currentIndex.value / questions.value.length) * 100)
})

onMounted(async () => {
  if (!sessionId) {
    ElMessage.error('缺少面试会话参数')
    loading.value = false
    return
  }
  try {
    const [sessionRes, questionsRes] = await Promise.all([
      getSession(sessionId) as Promise<InterviewSessionVO | null>,
      getSessionQuestions(sessionId) as Promise<InterviewQuestionVO[] | null>
    ])
    session.value = sessionRes
    questions.value = questionsRes || []
    if (sessionRes) {
      currentIndex.value = sessionRes.currentIndex || 0
    }
    // 回显当前题目已存的答案（历史题目，重进时可见）
    answerText.value = currentQuestion.value?.userAnswer || ''
  } catch (error) {
    console.error('加载面试数据失败:', error)
    ElMessage.error('加载面试失败，请稍后重试')
  } finally {
    loading.value = false
  }
})

const handleSubmit = async () => {
  const q = currentQuestion.value
  if (!q || !sessionId) return
  if (!answerText.value || answerText.value.trim() === '') {
    ElMessage.warning('请先填写回答内容')
    return
  }
  submitting.value = true
  try {
    const vo = await submitAnswer({
      sessionId,
      answer: answerText.value,
      currentIndex: currentIndex.value
    }) as InterviewSessionVO | null
    if (vo) {
      session.value = vo
      if (vo.status !== 'CREATED') {
        ElMessage.success('面试已提交，正在生成评估...')
        router.push(`/backend/record`)
        return
      }
      currentIndex.value = vo.currentIndex
      answerText.value = ''
      ElMessage.success('回答已提交，进入下一题')
    }
  } catch (error: any) {
    const errMsg = error?.response?.data?.message || error?.message || '提交失败'
    ElMessage.error(errMsg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="loading" class="exam-container">
    <div class="exam-header">
      <div class="header-title">
        <div class="title-icon"><el-icon><Monitor/></el-icon></div>
        <span class="title-text">模拟面试</span>
      </div>
      <span>正在加载面试数据...</span>
    </div>
  </div>

  <div v-else-if="!currentQuestion" class="exam-container">
    <div class="exam-header">
      <div class="header-title">
        <div class="title-icon"><el-icon><Monitor/></el-icon></div>
        <span class="title-text">模拟面试</span>
      </div>
      <span>暂无可用题目</span>
    </div>
  </div>

  <div v-else class="exam-container">
    <div class="exam-header">
      <div class="header-title">
        <div class="title-icon"><el-icon><Monitor/></el-icon></div>
        <span class="title-text">模拟面试</span>
      </div>
      <span>认真回答问题展示你的实力</span>
    </div>

    <div class="exam-progress">
      <div>题目 {{ currentIndex }} / {{ questions.length }}</div>
      <el-progress
          :percentage="progressPercent"
          :show-text="false"
          color="#626aef"
          :stroke-width="8"
      />
    </div>

    <div class="exam-content">
      <div class="content-question">
        <div class="in-content">
          <div class="normal-image"><el-icon><User /></el-icon></div>
          <div class="in-message">
            <div class="message-header">
              <span>面试官</span>
              <el-tag v-if="currentQuestion.topicSummary">{{ currentQuestion.topicSummary }}</el-tag>
            </div>
            <span class="in-message-content">{{ currentQuestion.questionContent }}</span>
          </div>
        </div>
        <div v-if="currentQuestion.userAnswer" class="user-content">
          <span class="user-message-content">{{ currentQuestion.userAnswer }}</span>
          <div class="normal-image"><el-icon><User /></el-icon></div>
        </div>
      </div>

      <div class="content-answer">
        <div class="answer-wrapper">
          <el-input
              class="answer-input"
              type="textarea"
              :rows="4"
              resize="none"
              placeholder="请输入你的回答..."
              v-model="answerText"
          ></el-input>
          <el-button
              type="primary"
              class="submit-btn"
              :icon="Promotion"
              :loading="submitting"
              @click="handleSubmit"
          >提交</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exam-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100vh;
}
.exam-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 70%;
  padding: 12px;
  gap:8px;
  box-sizing: border-box;
}
.header-title {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-icon {
  display: flex;
  align-content: center;
  font-size: 36px;
  color: #fff;
  background-color: #626aef;
  padding: 5px;
  border-radius: 8px;
}
.title-text {
  height: 100%;
  font-size:36px;
  line-height: 48px;
  font-weight: bold;
}
.exam-progress{
  background-color: #ffffff;
  margin-top: 10px;
  width: 70%;
  padding: 20px;
  border-radius: 12px;
  box-sizing: border-box;
}
.exam-content{
  width: 70%;
  height: 70vh;
  min-height: 500px;
  background-color: #ffffff;
  border-radius: 20px;
  margin-top: 10px;
  box-sizing: border-box;
  display:flex;
  flex-direction: column;

}
.content-question{
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  gap:15px;
  height: 100%;
  box-sizing: border-box;
  padding: 20px;
}
.in-content{
  display: flex;
  gap:6px;
}
.normal-image{
  width:30px;
  height:30px;
  background-color: #7b82f3;
  color: #ffffff;
  border-radius:50%;
  align-self: flex-start;
  box-sizing: border-box;
  padding:3px;
  display:flex;
  justify-content: center;
  align-items: center;
}
.in-message{
  display: flex;
  flex-direction: column;
  gap:4px;
}
.message-header{
  display: flex;
  gap:4px;
}
.in-message-content{
  box-sizing: border-box;
  padding:10px;
  border-radius:20px;
  background-color:#f0f2ff;
  color:#333333;
  font-size: 14px;
  font-weight:325;
}
.user-content{
  display: flex;
  gap:6px;
  justify-content: flex-end;
}
.user-message-content{
  box-sizing: border-box;
  padding:10px;
  border-radius:20px;
  background-color:#626aef;
  color:#ffffff;
  font-weight:325;
  font-size: 14px;
}
.content-answer{
  background-color: #e9ebf0;
  height: 140px;
  border-top: 1px solid #ebeef5;
  border-radius: 0 0 20px 20px;
  padding: 15px 20px;
  box-sizing: border-box;
}

/* 输入框容器，使用相对定位方便把按钮放在右下角 */
.answer-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

/* 重写 Element Plus 默认的输入框样式 */
:deep(.answer-input .el-textarea__inner) {
  height: 96px !important;
  min-height: 96px !important;
  max-height: 96px !important;
  background-color: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 12px;
  padding-bottom: 40px;  /* 给底部按钮留出空间 */
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  resize: none;
  font-family: inherit;
  font-size: 14px;
  transition: box-shadow 0.3s;
}

:deep(.answer-input .el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(98, 106, 239, 0.2);
  border-color: #4b53d8;
}

/* 悬浮在右下角的精美按钮 */
.submit-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  border-radius: 8px;
  padding: 8px 20px;
  font-weight: 500;
  box-shadow: 0 2px 6px rgba(98, 106, 239, 0.3);
  background-color: #626aef;
  border-color: #626aef;
}

.submit-btn:hover {
  background-color: #7b82f3;
  border-color: #7b82f3;
}
</style>