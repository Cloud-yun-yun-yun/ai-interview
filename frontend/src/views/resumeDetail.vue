<script setup lang="ts">

import {ArrowLeft, ArrowRight, EditPen, TrendCharts} from "@element-plus/icons-vue";
import { useRouter, useRoute } from 'vue-router';
import {getResumeDetail, type ResumeAnalysisResponse} from '@/api/business';
import {onMounted, ref} from "vue";

const router = useRouter();
const route = useRoute();
const resumeDetail = ref<ResumeAnalysisResponse>()

onMounted(async ()=>{
  resumeDetail.value = await getResumeDetail(Number(route.params.id))  || {}
  console.log(resumeDetail.value)
})
const handleBack = () => {
  router.push('/backend/resume');
}
const handleInterview = () => {
  router.push('/backend/interview');
}
</script>

<template>
  <div class="resume-detail-container">
    <div class="resume-detail-header">
      <div class="header-left">
        <el-button
            color="#626aef"
            :icon="ArrowLeft"
            @click="handleBack"
            style="height: 40px;
            border-radius: 15px;"
        />
        <div class="card-header-title" style="font-size: 24px;">
          <span>简历详情</span>
        </div>
      </div>
      <div >
        <el-button
            color="#626aef"
            style="height: 40px;
            border-radius: 15px;"
            @click="handleInterview"
        >
          去面试<el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="resume-detail-content">
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="custom-card left-card">
            <!-- 头部 -->
            <div class="card-header">
              <div class="card-header-title"><el-icon><TrendCharts /></el-icon> 核心评价</div>
            </div>
            <!-- 浅绿色内容区 -->
            <div class="evaluation-content">
              <div class="summary-text">
                {{ resumeDetail?.summary || '暂无核心评价' }}
              </div>

              <!-- 分数与时间区块 -->
              <div class="score-row">
                <div class="score-box">
                  <span class="label">总分</span>
                  <div class="score-val">{{ resumeDetail?.overallScore || '暂无总分' }} <span class="small">/ 100</span></div>
                </div>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <!-- 给父容器加上 progress-grid 类 -->
          <div class="custom-card progress-grid">

            <!-- 第1个：项目经验 (会自动独占整行) -->
            <div class="progress-item full-width">
              <div class="progress-label">
                <span>项目经验</span> <span>{{ resumeDetail?.projectScore || '暂无项目经验' }}/40</span>
              </div>
              <el-progress
                  :percentage="(resumeDetail?.projectScore || 0) / 40 * 100"
                  :show-text="false"
                  color="#a855f7"
                  :stroke-width="8"
              />
            </div>

            <!-- 第2个：技能匹配 -->
            <div class="progress-item">
              <div class="progress-label">
                <span>技能匹配</span> <span>{{ resumeDetail?.skillMatchScore || '暂无技能匹配' }}/20</span>
              </div>
              <el-progress
                  :percentage="(resumeDetail?.skillMatchScore || 0) / 20 * 100"
                  :show-text="false"
                  color="#3b82f6"
                  :stroke-width="8"
              />
            </div>

            <!-- 第3个：内容完整性 -->
            <div class="progress-item">
              <div class="progress-label">
                <span>内容完整性</span> <span>{{ resumeDetail?.contentScore || '暂无内容完整性' }}/15</span>
              </div>
              <el-progress
                  :percentage="(resumeDetail?.contentScore || 0) / 15 * 100"
                  :show-text="false"
                  color="#10b981"
                  :stroke-width="8"
              />
            </div>

            <!-- 第4个：结构清晰度 -->
            <div class="progress-item">
              <div class="progress-label">
                <span>结构清晰度</span> <span>{{ resumeDetail?.structureScore || '暂无结构清晰度' }}/15</span>
              </div>
              <el-progress
                  :percentage="(resumeDetail?.structureScore || 0) / 15 * 100"
                  :show-text="false"
                  color="#06b6d4"
                  :stroke-width="8"
              />
            </div>

            <!-- 第5个：表达专业性 -->
            <div class="progress-item">
              <div class="progress-label">
                <span>表达专业性</span> <span>{{ resumeDetail?.expressionScore || '暂无表达专业性' }}/10</span>
              </div>
              <el-progress
                  :percentage="(resumeDetail?.expressionScore || 0) / 10 * 100"
                  :show-text="false"
                  color="#f97316"
                  :stroke-width="8"
              />
            </div>

          </div>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <div class="custom-card suggestion-card">
            <div class="card-header">
              <div class="card-header-title"><el-icon><EditPen /></el-icon> 建议</div>
            </div>
            <div class="suggestion-list">
              <div class="suggestion-content" v-for="(item, index) in resumeDetail?.suggestions || []" :key="index">
                <div class="suggestion-header">
                  <el-tag type="warning">{{ item.priority }}</el-tag>
                  <el-tag type="warning">{{ item.category }}</el-tag>
                </div>
                <div class="text-small" style="font-weight: bold;">{{ item.issue }}</div>
                <div class="text-small">{{ item.recommendation }}</div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>

</template>

<style scoped>
.resume-detail-container{
  padding:20px;
}
.resume-detail-header {
  display: flex;
  justify-content: space-between;
  padding: 20px;
  background-color: #ffffff;
  border-radius: 16px;
  margin-bottom: 15px;
}
.header-left {
  display: flex;
  gap: 15px;
}
.resume-detail-content{
  min-height: 100vh;
  gap:15px;
  display: flex;
  flex-direction: column;
}

.custom-card {
  background-color: #ffffff;
  border-radius: 16px; /* 大圆角 */
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03); /* 极浅的阴影，看起来更有质感 */
  height: 100%;
  box-sizing: border-box;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.card-header-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/*左 */
.left-card {
  background-color: #ffffff;
}
.evaluation-content {
  background-color: #eef8f1;
  border-radius: 12px;
  padding: 24px;
}
/*右 */
.progress-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.progress-item {
  padding: 8px 12px;
  background-color: #f8fafc;
  border-radius: 12px;
}
.resume-detail-footer{
  margin-top: 20px;
}
.suggestion-card {
  background-color: #ffffff;
}
.suggestion-list {
  display: flex;
  flex-direction: column; /* 垂直排列 */
  gap: 16px; /* 👈 控制卡片之间的间距 */
}
.suggestion-content {
  display: flex;
  flex-direction: column;
  gap: 3px;
  background-color: antiquewhite;
  border-radius: 16px;
  padding: 20px;
}

.suggestion-header {
  display: flex;
  gap:6px;
  align-items: center;
}

.text-small {
  font-size: 16px;
  color: #666;
}

</style>