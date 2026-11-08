<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSessionList, type InterviewSessionVO } from "@/api/business"
import { ElMessage } from "element-plus"
import { Monitor, VideoPlay, CircleCheck } from "@element-plus/icons-vue"
const router = useRouter()

const sessions = ref<InterviewSessionVO[]>([])
const loading = ref(true)
const POLL_INTERVAL = 3000;
let pollTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(async () => {
  await loadSessions()
  checkTask()
})
//停止轮询
const stopPolling = ()=>{
  if(pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}
const checkTask = ()=>{
  stopPolling()
  pollTimer = setTimeout(async ()=>{
    try{
      const res = await getSessionList()
      console.log(res)
      sessions.value = res
      const hasEvaluating = res.some((item) => item.status === 'EVALUATING');
      if(hasEvaluating) {
        checkTask()
      }
      else {
        stopPolling()
      }
    }catch(error) {
      console.error('轮询检查任务失败:', error)
      ElMessage.error('轮询检查任务失败，请稍后重试')
      stopPolling()
    }
   }, POLL_INTERVAL)
}

const loadSessions = async () => {
  loading.value = true
  try {
    const res = await getSessionList() as InterviewSessionVO[] | null
    sessions.value = res || []
  } catch (error) {
    console.error('获取面试记录失败:', error)
    ElMessage.error('获取面试记录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const isInProgress = (s: InterviewSessionVO) => s.sessionStatus === 'CREATED'

const handleContinue = (s: InterviewSessionVO) => {
  router.push(`/backend/interview/exam/${s.sessionId}`)
}

const handleRowClick = (s: InterviewSessionVO) => {
  router.push(`/backend/record/evaluate/${s.sessionId}`)
}

const statusTag = (s: InterviewSessionVO) => {
  if (s.sessionStatus === 'CREATED') return { type: 'primary' as const, text: '进行中' }
  if (s.sessionStatus === 'SUBMITTED') return { type: 'warning' as const, text: '评估中' }
  if (s.sessionStatus === 'EVALUATED') return { type: 'success' as const, text: '已完成' }
  return { type: 'info' as const, text: s.sessionStatus }
}
</script>

<template>
  <div class="record-container">
    <div class="record-header">
      <div class="header-left">
        <div class="title">
          <el-icon style="color: #626aef;"><Monitor/></el-icon>
          <span>面试记录</span>
        </div>
        <div class="sub-title">可继续进行中的面试</div>
      </div>
      <div class="header-right">
        <el-button
            size="small"
            color="#626aef"
            style="height: 40px; border-radius: 15px;"
            :loading="loading"
            @click="loadSessions"
        >刷新</el-button>
      </div>
    </div>

    <el-table
        class="record-table"
        v-loading="loading"
        :data="sessions"
        style="width: 100%; border-radius: 20px;"
        empty-text="暂无面试记录"
        @row-click="handleRowClick"
    >
      <el-table-column label="名称" prop="sessionName" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.sessionName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" min-width="120" align="center">
        <template #default="{ row }">
          <span>{{ row.createTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTag(row).type" effect="light">{{ statusTag(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前进度" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.status === 'CREATED'">第 {{ row.currentIndex }} 题</span>
          <span v-else>已完成</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="120" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
              v-if="isInProgress(row)"
              type="primary"
              link
              :icon="VideoPlay"
              @click.stop="handleContinue(row)"
          >继续作答</el-button>
          <el-icon v-else-if="row.status === 'EVALUATED'" color="#67c23a"><CircleCheck/></el-icon>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.record-container {
  padding: 20px;
}
.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  margin-bottom: 20px;
  background-color: #ffffff;
  border-radius: 20px;
}
.header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.header-right {
  display: flex;
  align-items: center;
}
.title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 24px;
  font-weight: bold;
  color: #333;
  line-height: 1.8;
}
.sub-title {
  color: #909399;
  font-size: 14px;
}
.record-table {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
:deep(.record-table .el-table__body .el-table__row) {
  cursor: pointer;
}
:deep(.record-table th.el-table__cell) {
  color: #333;
  font-weight: 600;
  background-color: #ffffff;
}
:deep(.record-table .el-table__body td.el-table__cell) {
  height: 60px;
  color: #606266;
  font-size: 14px;
}
</style>