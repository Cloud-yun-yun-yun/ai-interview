<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {type AsyncTaskStatus, getSessionList, getVoiceSessionList, type InterviewSessionVO, type VoiceSessionVO, removeSession, deleteVoiceSession} from "@/api/business"
import {ElMessage, ElMessageBox} from "element-plus"
import {
  Monitor,
  VideoPlay,
  CircleCheck,
  CirclePlus,
  Loading,
  CircleClose,
  Delete
} from "@element-plus/icons-vue"
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
      const [textRes, voiceRes] = await Promise.all([
        getSessionList(),
        getVoiceSessionList(),
      ])
      const textSessions = (textRes || []) as InterviewSessionVO[]
      const voiceSessions = (voiceRes || []).map(v => toSessionVO(v))
      const res = [...textSessions, ...voiceSessions]
      sessions.value = res
      const hasEvaluating = res.some((item) =>
          item.sessionStatus === 'SUBMITTED' &&
          item.sessionEvaluateStatus !== 'COMPLETED' &&
          item.sessionEvaluateStatus !== 'FAILED'
      );
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
    const [textRes, voiceRes] = await Promise.all([
      getSessionList(),
      getVoiceSessionList(),
    ])
    const textSessions = (textRes || []) as InterviewSessionVO[]
    const voiceSessions = (voiceRes || []).map(v => toSessionVO(v))
    sessions.value = [...textSessions, ...voiceSessions]
  } catch (error) {
    console.error('获取面试记录失败:', error)
    ElMessage.error('获取面试记录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 将语音面试会话映射为与文字面试一致的展示结构
const toSessionVO = (v: VoiceSessionVO): InterviewSessionVO => ({
  sessionId: v.sessionId,
  userId: v.userId,
  currentIndex: v.currentTurn,
  sessionStatus: v.sessionStatus,
  sessionQuestionGenStatus: undefined as any,
  sessionEvaluateStatus: v.evaluationStatus,
  createTime: v.createTime,
  sessionName: v.sessionName,
  totalScore: v.totalScore == null ? undefined as any : String(v.totalScore),
  feedback: v.feedback,
  type: v.type || 'voice',
})

const isInProgress = (s: InterviewSessionVO) => s.sessionStatus === 'CREATED'

const handleContinue = (s: InterviewSessionVO) => {
  if(s.type==='text') {
    router.push(`/backend/interview/exam/${s.sessionId}`)
  }
  else if(s.type==='voice') {
    router.push(`/backend/voice/interview/exam/${s.sessionId}`)
  }
}

const handleRowClick = (s: InterviewSessionVO) => {
  if (s.type === 'voice') {
    return
  }
  router.push(`/backend/record/evaluate/${s.sessionId}`)
}

const statusMap: Record<AsyncTaskStatus, { icon: any; text: string; color: string }> = {
  PENDING: { icon: CirclePlus, text: '待分析', color: '#909399' },
  PROCESSING: { icon: Loading, text: '分析中', color: '#e6a23c' },
  COMPLETED: { icon: CircleCheck, text: '已完成', color: '#67c23a' },
  FAILED: { icon: CircleClose, text: '失败', color: '#f56c6c' },
};
const handleDelete = async (row: InterviewSessionVO | VoiceSessionVO)=>{
  try {
    // 1. 弹出确认框
    await ElMessageBox.confirm(
        '是否删除该记录？删除后将不可恢复。',
        '警告',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
        }
    )
    if (row.type === 'voice') {
      await deleteVoiceSession(row.sessionId)
    } else {
      await removeSession(row.sessionId)
    }
    sessions.value = sessions.value.filter(item => item.sessionId !== row.sessionId)
    ElMessage.success('删除成功')
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') {
      return
    }
    console.error('删除失败', error)
  }
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
      <el-table-column label="类型" min-width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'voice'" type="success" effect="light">语音面试</el-tag>
          <el-tag v-else effect="light">文字面试</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" min-width="120" align="center">
        <template #default="{ row }">
          <span>{{ row.createTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="评估状态" min-width="100" align="center">
        <template #default="{ row }">
          <template v-if="row.sessionEvaluateStatus && row.sessionStatus !== 'CREATED'">
            <div
                class="status-text-wrapper"
                :style="{ color: statusMap[row.sessionEvaluateStatus]?.color }"
            >
              <el-icon v-if="statusMap[row.sessionEvaluateStatus]?.icon">
                <component :is="statusMap[row.sessionEvaluateStatus]?.icon" />
              </el-icon>
              <el-icon v-else><CirclePlus /></el-icon>
              <span>{{ statusMap[row.sessionEvaluateStatus]?.text || row.sessionEvaluateStatus}}</span>
            </div>
          </template>
          <template v-else>
              <span>请先完成考试</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="当前进度" min-width="100" align="center">
        <template #default="{ row }">
          <template v-if="row.type === 'voice'">
            <span v-if="row.sessionStatus === 'FINISHED'" style="color: #909399;">已完成</span>
            <span v-else style="color: #606266;">进行中</span>
          </template>
          <template v-else>
            <span v-if="row.status === 'CREATED'">第 {{ row.currentIndex }} 题</span>
            <span v-else>已完成</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="120"  align="center">
        <template #default="{ row }">
          <el-button
              v-if="isInProgress(row)"
              type="primary"
              link
              :icon="VideoPlay"
              @click.stop="handleContinue(row)"
          >继续作答</el-button>
          <div class="status-text-wrapper"
               v-else-if="row.sessionStatus === 'EVALUATED'">
            <el-icon  color="#67c23a"><CircleCheck/></el-icon>
            <span style="color: #67c23a;">查看评估</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="删除" width="100" align="center">
        <template  #default="{ row }">
          <el-icon @click.stop="handleDelete(row)"
                   class="delete-icon"
                   size="16" >
            <Delete/></el-icon>
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
.status-text-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 14px;
}
.delete-icon {
  cursor: pointer;
  transition: color 0.2s;
}
.delete-icon:hover {
  color: #f56c6c;
}
</style>