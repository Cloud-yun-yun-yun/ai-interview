<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {type AsyncTaskStatus, getSessionList, type InterviewSessionVO, removeSession} from "@/api/business"
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
      const res = await getSessionList()
      console.log(res)
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

const statusMap: Record<AsyncTaskStatus, { icon: any; text: string; color: string }> = {
  PENDING: { icon: CirclePlus, text: '待分析', color: '#909399' },
  PROCESSING: { icon: Loading, text: '分析中', color: '#e6a23c' },
  COMPLETED: { icon: CircleCheck, text: '已完成', color: '#67c23a' },
  FAILED: { icon: CircleClose, text: '失败', color: '#f56c6c' },
};
const handleDelete = async (sessionId:string)=>{
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
    await removeSession(sessionId)
    sessions.value = sessions.value.filter(item => item.sessionId !== sessionId)
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
          <span v-if="row.status === 'CREATED'">第 {{ row.currentIndex }} 题</span>
          <span v-else>已完成</span>
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
          <el-icon @click.stop="handleDelete(row.sessionId)"
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