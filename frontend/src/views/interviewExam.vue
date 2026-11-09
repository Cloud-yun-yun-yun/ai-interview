<script setup lang="ts">
import {computed, onMounted, ref, nextTick} from "vue";
import  {getSession, getSessionQuestions, submitAnswer,
  type InterviewQuestionVO, type InterviewSessionVO, type QuestionAnswer} from "@/api/business.ts";
import {Loading, Monitor, Promotion, User} from "@element-plus/icons-vue";
import { useRoute , useRouter} from 'vue-router'
import {ElMessage} from "element-plus";

const route = useRoute()
const router = useRouter()

const currentQuestions = ref<InterviewQuestionVO[]>([])
const questions = ref<InterviewQuestionVO[]>([])
const session = ref<InterviewSessionVO>()
const submitting = ref(false)
const currentIndex = ref<number>()
const answerText = ref()
const sessionId = route.params.sessionId
const chatScrollRef = ref<HTMLElement | null>(null)
//动态获取进度条
const progressPercent = computed(() => {
  if (questions.value.length === 0) return 0
  return Math.round((currentIndex.value / questions.value.length) * 100)
})
//滚动至底部的方法
const scrollToBottom = async ()=>{
  // 等待 DOM 更新完毕
  await nextTick()
  if (chatScrollRef.value){
    chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
  }
}
onMounted(async()=>{
  session.value = await getSession(sessionId as any)
  if (session.value) {
    currentIndex.value = session.value.currentIndex || 0
  }
  questions.value = await getSessionQuestions(sessionId as any)
  if (questions.value.length > 0) {
    currentQuestions.value = questions.value.filter(
        (q) => q.questionIndex <= currentIndex.value
    )
    scrollToBottom()
  }
})

const handleSubmit = async ()=>{
  if (!answerText.value.trim()) return;
  submitting.value = true;
  const payload: QuestionAnswer = {
    sessionId: sessionId as string,
    answer: answerText.value,
    currentIndex: currentIndex.value
  }
  // 发送请求
  try{
    session.value = await submitAnswer(payload)
    if(session.value.sessionStatus === 'SUBMITTED'){
      ElMessage.success("提交成功，准备评估")
      router.push({ name: 'record' })
      return
    }
    const nextIdx = session.value.currentIndex;
    currentQuestions.value[currentQuestions.value.length - 1].userAnswer = answerText.value;
    answerText.value = '';
    scrollToBottom()
    const nextQuestion = questions.value.find((q) => q.questionIndex === nextIdx)
    setTimeout(()=>{
      currentIndex.value = nextIdx;
      currentQuestions.value.push(nextQuestion);
      scrollToBottom();
    },500)
  }catch(error){
    ElMessage.warning("没有下一题了");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div v-if="currentQuestions.length === 0" class="loading-view">
    <el-icon class="loading-icon" :size="56">
      <Loading />
    </el-icon>
    <p class="loading-text">AI 正在为您生成面试题目...</p>
    <p class="loading-sub">请稍候，这可能需要几秒钟</p>
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
      <div class="content-question" ref="chatScrollRef">
        <div class="question-item"
             v-for="currentQuestion in currentQuestions"
             :key="currentQuestion.questionIndex">
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
/* Loading 视图 */
.loading-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 80vh;
  gap: 16px;
  background: radial-gradient(circle at center, #2c2c2c 0%, #1a1a1a 100%);
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
.question-item{
  display: flex;
  flex-direction: column;
  gap:15px;
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
  flex-shrink: 0;
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