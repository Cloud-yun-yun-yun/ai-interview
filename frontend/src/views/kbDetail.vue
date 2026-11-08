<script setup lang="ts">
import {ArrowLeft} from "@element-plus/icons-vue";
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted } from 'vue'
import { getKnowledgeBaseQuestions, type KnowledgeBaseQuestionVO } from '@/api/business'

const router = useRouter()
const route = useRoute()

const questions = ref<KnowledgeBaseQuestionVO[]>([])

const loadQuestions = async () => {
  const kbId = route.params.id as string
  questions.value = await getKnowledgeBaseQuestions(kbId)
}

onMounted(loadQuestions)

const goKnowledgeBase = () => {
  router.push({ name: 'knowledgeBase' })
}
</script>

<template>
  <div class="kbDetail-container">
    <!-- 顶部返回按钮行（与卡片左侧对齐） -->
    <div class="header-action">
      <el-button color="#626aef" class="back-btn" circle @click="goKnowledgeBase">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="back-text">返回知识库</span>
    </div>

    <!-- 卡片列表 -->
    <div class="question-list">
      <el-empty v-if="questions.length === 0" description="暂未生成题目" />
      <div class="question-card" v-for="(q, index) in questions" :key="index">
        <div class="question-targ">
          <span class="tag-number">{{ index + 1 }}</span>
          <span class="tag-lang">{{ q.type }}</span>
          <span class="tag-title">{{ q.topicSummary }}</span>
        </div>
        <div class="question-content">
          {{ q.question }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kbDetail-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  gap: 20px;
  min-height: 100%;
  background-color: #f7f8fc;
}
.header-action {
  width: 800px;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  margin-bottom: 10px;
}
.back-btn {
  width: 42px !important;
  height: 42px !important;
  border-radius: 10px !important;
  border: none !important;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.back-btn:hover {
  opacity: 0.8;
}

.back-btn .el-icon {
  font-size: 22px ;
  font-weight: bold;
}

.back-text {
  font-size: 30px;
  font-weight: bold;
  color: #303133;
  display: flex;
  align-items: center;
  height: 42px;
}

.question-list {
  width: 800px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 40px;
}

.question-card {
  background-color: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: transform 0.2s, box-shadow 0.2s; /* 鼠标悬停时的动效 */
}

/* 鼠标悬停卡片时微微上浮 */
.question-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.06);
}

.question-targ {
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
  color: #626aef;
  font-size: 15px;
  font-weight: 500;
}

.tag-title {
  font-weight: 500;
  color: #303133;
}

.question-content {
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
}
</style>