<script setup lang="ts">
import {ArrowLeft, ChatDotRound, TrendCharts} from "@element-plus/icons-vue";
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getSession, getSessionQuestions,
  type InterviewSessionVO, type InterviewQuestionVO } from '@/api/business'

const router = useRouter()
const route = useRoute()

const session = ref<InterviewSessionVO | null>(null)
const sessionQuestions = ref<InterviewQuestionVO[]>([])

const loadData = async () => {
  const sessionId = route.params.sessionId as string
  session.value = await getSession(sessionId)
  sessionQuestions.value = await getSessionQuestions(sessionId)
}

onMounted(loadData)

const goRecord = () => {
  router.push({ name: 'record' })
}
</script>

<template>
  <div class="evaluate-container">
    <div class="evaluate-title">
      <el-button
          color="#626aef"
          class="evaluate-title-icon"
          @click="goRecord"
      >
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="title-text">面试记录</span>
    </div>
    <div class="evaluate-content" v-if="session">
      <div class="evaluate-score" >
        <el-progress type="circle" :percentage="Number(session.totalScore) || 0" :width="80" color="#7471F0">
          <template #default>
            <span class="score-text">{{ session.totalScore || 0 }}<span class="score-unit">分</span></span>
          </template>
        </el-progress>
        <div class= "evaluate-content-title">
          <el-icon class="title-icon"><TrendCharts /></el-icon>
          <span class="info-label">面试评估</span>
        </div>
        <span class="info-feedback">{{ session.feedback || '暂无反馈' }}</span>
      </div>
    </div>
    <div class="records-title">
      <el-icon><ChatDotRound /></el-icon>
      <span>问答记录</span>
    </div>
    <div class="exam-records">
      <div class="exam-card" v-for="(q, index) in sessionQuestions" :key="index">
        <el-empty v-if="sessionQuestions.length === 0" description="暂无问答记录" />
        <div class="exam-records-targ">
          <span class="tag-number">{{ index + 1 }}</span>
          <span class="tag-lang">{{ q.type }}</span>
          <span class="tag-title">{{ q.topicSummary }}</span>
        </div>
        <div class="exam-records-question">
          {{ q.questionContent }}
        </div>
        <div class="exam-records-answer-wrapper">
          <div class="answer-label">
            <el-icon><ChatDotRound /></el-icon>
            <span>你的回答</span>
          </div>
          <div class="exam-records-answer">
            <span>{{ q.userAnswer }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.evaluate-container{
  display:flex;
  flex-direction: column;
  align-items: center;
  padding:20px;
  gap:20px;
  height: 100%;
}
.evaluate-title{
  width: 800px;
  align-items: center;
  display:flex;
  gap:15px;
  font-weight: bold;
}
.evaluate-title-icon{
  width: 32px;
  height: 32px ;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.evaluate-title-icon:hover {
  opacity: 0.8;
  cursor: pointer;
}
.title-text {
  font-size: 22px;
  font-weight: 600;
  color: #303133;/* 深色字，显得更正式 */
}
.evaluate-content{
  display:flex;
  gap:20px;
  flex-direction: column;
}
.evaluate-score{
  width:800px;
  background-color: #ffffff;
  border-radius: 25px;
  display:flex;
  flex-direction: column;
  gap:15px;
  align-items: center;
  box-sizing:border-box;
  padding:20px;
  transition: transform 0.2s, box-shadow 0.2s; /* 鼠标悬停时的动效 */
}
.evaluate-content-title{
  display:flex;
  align-items: center;
  gap:8px;
}
/* 鼠标悬停卡片时微微上浮 */
.evaluate-score:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.06);
}
.evaluate-score :deep(.el-progress--circle) {
  margin-right: 15px;
}
.info-feedback {
  font-size: 13px;
  color: #303133;
  line-height: 1.8;
  text-align: justify;
  word-break: break-word;
  padding:15px;
  background-color: #f0f2ff;
  border-radius: 15px;
}
.evaluate-score :deep(.el-progress-circle) {
  width: 160px !important;
  height: 160px !important;
}
.evaluate-score :deep(.el-progress-circle__path) {
  stroke: #7471F0 !important;
}
.evaluate-score :deep(.el-progress__text) {
  font-size: 30px !important;
  color: #7471F0 !important;
}
.records-title{
  display:flex;
  align-items: center;
  width: 800px;
  gap:8px;
}
.title-icon {
  font-size: 22px ;
  color: #606266;
}
.exam-records {
  width: 800px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.exam-card {
  background-color: #ffffff;
  border-radius: 20px;
  padding: 24px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: transform 0.2s, box-shadow 0.2s; /* 鼠标悬停时的动效 */
}
.exam-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.06);
}
.exam-records-targ {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: #333;
}
.tag-number {
  font-weight: bold;
  color: #909399;
}
.tag-lang {
  color: #7471F0;
  font-size: 15px;
  font-weight: 300;
}
.tag-title {
  font-weight: 500;
}
.exam-records-question {
  font-size: 15px;
  color: #303133;
  line-height: 1.6;
}
.exam-records-answer-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}
.answer-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 14px;
}
.exam-records-answer {
  padding: 16px 20px;
  box-sizing: border-box;
  background-color: #f0f2ff;
  border-radius: 12px;
  color: #303133;
  font-size: 14px;
}
</style>