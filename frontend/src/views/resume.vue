<script setup lang="ts">
import { useRouter } from 'vue-router';
import { onMounted, onUnmounted, ref} from 'vue';
import {
  ArrowRight,
  DataAnalysis,
  Delete,
  Document,
  Files,
  CirclePlus,
  Loading,
  CircleCheck,
  CircleClose
} from "@element-plus/icons-vue";
import {getResumeList, type ResumeVO, type AsyncTaskStatus, deleteResume} from "@/api/business";
import { analysisResume } from "@/api/business";
import { ElMessage, ElMessageBox } from "element-plus";
const resumeList = ref<ResumeVO[]>([]);
const router = useRouter();

const POLL_INTERVAL = 3000;
let pollingTimer: ReturnType<typeof setTimeout> | null = null;

const statusMap: Record<AsyncTaskStatus, { icon: any; text: string; color: string }> = {
  PENDING: { icon: CirclePlus, text: '待分析', color: '#909399' },
  PROCESSING: { icon: Loading, text: '分析中', color: '#e6a23c' },
  COMPLETED: { icon: CircleCheck, text: '已完成', color: '#67c23a' },
  FAILED: { icon: CircleClose, text: '失败', color: '#f56c6c' },
};
const analysis = async (resumeId: number) => {
  try {
    await analysisResume(resumeId);
    startPolling();
  } catch (e) {
    console.error(e);
  }
}

const stopPolling = () => {
  if (pollingTimer !== null) {
    clearTimeout(pollingTimer);
    pollingTimer = null;
  }
};

const startPolling = () => {
  stopPolling();
  pollingTimer = setTimeout(async () => {
    try{
      const res = await getResumeList()
      resumeList.value = res
      const hasAnaylising = res.some((item)=>item.asyncTaskStatus !== 'COMPLETED' && item.asyncTaskStatus !== 'FAILED')
      if(hasAnaylising) {
        startPolling
      }
      else {
        stopPolling()
      }
    }catch(error){
      console.error('轮询检查任务失败:', error)
      ElMessage.error('轮询检查任务失败，请稍后重试')
      stopPolling()
    }
  }, POLL_INTERVAL)
};

const handleDelete = async (resumeId: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该简历吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteResume(resumeId);
    ElMessage.success('删除成功');
    resumeList.value = resumeList.value.filter(item => item.id !== resumeId);
  } catch (e: any) {
    if (e === 'cancel' || e?.toString?.() === 'cancel') {
      return;
    }
    ElMessage.error('删除失败');
    console.error('删除失败:', e);
  }
}

const handleRowClick = (row: ResumeVO) => {
  router.push(`/backend/resume/detail/${row.id}`)
}
const handleInterview = () => {
  router.push('/backend/interview');
}

onMounted(async ()=>{
  const res  = await getResumeList()
  console.log(res)
  resumeList.value = res || []
  startPolling();
})

onUnmounted(() => {
  stopPolling();
})
</script>

<template>
  <div class="resume-container">
    <div class="resume-header">
      <div class="header-left">
        <div class="title">
          <el-icon style="color: #626aef;"><Files /></el-icon>
          <span >简历库</span>
        </div>
        <div class="sub-title">
          支持AI分析简历
        </div>
      </div>
      <div class="header-right">
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
    <div class="resume-content">
      <el-table
          :data="resumeList"
          style="width: 100%;
                 border-radius: 20px;"
          @row-click="handleRowClick"
          row-style="{ cursor: 'pointer' }"
      >
        <el-table-column prop="name" label="简历名称" min-width="250">
          <template #default="scope">
            <div class="name-with-icon">
              <el-icon><Document /></el-icon>
              <span>{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="textLength" label="简历字数" width="200">
          <template #default="scope">
            <span>{{ scope.row.textLength }} 字</span>
          </template>
        </el-table-column>

        <el-table-column label="分析状态" width="150" align="center">
          <template #default="scope">
            <template v-if="scope.row.asyncTaskStatus">
              <!-- 用 flex 容器包裹图标和文字 -->
              <div
                  class="status-text-wrapper"
                  :style="{ color: statusMap[scope.row.asyncTaskStatus]?.color }"
              >
                <el-icon>
                  <component :is="statusMap[scope.row.asyncTaskStatus]?.icon" />
                </el-icon>
                <span>{{ statusMap[scope.row.asyncTaskStatus]?.text || scope.row.asyncTaskStatus }}</span>
              </div>
            </template>
            <template v-else>
              <span style="color: #909399;">未分析</span>
            </template>
          </template>
        </el-table-column>

        <el-table-column prop="interviewStatus" label="面试状态" width="150" align="center">
          <template #default="scope">
            <el-tag
                :type="scope.row.interviewStatus === 'ACCEPT' ? 'success' : 'warning'"
                effect="light"
            >
              {{ scope.row.interviewStatus === 'ACCEPT' ? '可面试' : '不可面试' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button
                type="primary"
                link
                :icon="DataAnalysis"
                :disabled="scope.row.asyncTaskStatus === 'COMPLETED' || scope.row.asyncTaskStatus === 'PROCESSING'"
                @click.stop="analysis(scope.row.id)"
            >
              {{ scope.row.asyncTaskStatus === 'FAILED' ? '重试' : '分析' }}
            </el-button>

            <el-button
                type="danger"
                link
                :icon="Delete"
                @click.stop="handleDelete(scope.row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.resume-container {
  padding: 20px;
}
.name-with-icon {
  display: flex;
  align-items: center;
  gap: 6px;
}
.name-with-icon .el-icon {
  color: #909399;
}
.resume-header {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 20px;
  display: flex;
  padding: 20px;
  justify-content: space-between;
  background-color: #ffffff;
  border-radius: 20px;
}
.header-left{
  display:flex;
  flex-direction: column;
  gap: 10px;
}
.header-right{
  display: flex;
  align-items: center;
}
.title {
  font-size: 24px;
  color: #333;
  font-weight: bold;
  line-height: 1.8;
  display: flex;
  align-items: center;
  gap: 6px;
}
:deep(.el-table th.el-table__cell) {
  color: #333;
  font-weight: 600;
}
:deep(.el-table__body td.el-table__cell) {
  height: 60px;
}
/* 图标和文字水平居中对齐 */
.status-text-wrapper {
  display: flex;
  align-items: center;
  justify-content: center; /* 如果你的列设置了 align="center"，这里会居中显示 */
  gap: 4px; /* 图标和文字之间的间距 */
  font-size: 14px;
}
</style>