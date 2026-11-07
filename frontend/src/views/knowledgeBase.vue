<script setup lang="ts">
import { useRouter } from 'vue-router';
import { onMounted, ref } from 'vue';
import {
  ArrowRight, Delete, Document, Reading, CirclePlus, Loading, CircleCheck, CircleClose
} from "@element-plus/icons-vue";
import {
  getKnowledgeBaseList,
  deleteKnowledgeBase,
  type KnowledgeBaseVO,
  type AsyncTaskStatus,
  type QuestionGenStatus
} from "@/api/business";
import { ElMessage, ElMessageBox } from "element-plus";

const router = useRouter();

const kbList = ref<KnowledgeBaseVO[]>([]);
const loading = ref(false);

const vectorStatusMap: Record<AsyncTaskStatus, { icon: any; text: string; color: string }> = {
  PENDING: { icon: Loading, text: '向量化中', color: '#e6a23c' },
  PROCESSING: { icon: Loading, text: '向量化中', color: '#e6a23c' },
  COMPLETED: { icon: CircleCheck, text: '已就绪', color: '#67c23a' },
  FAILED: { icon: CircleClose, text: '失败', color: '#f56c6c' },
};

const questionGenStatusMap: Record<QuestionGenStatus, { icon: any; text: string; color: string }> = {
  NONE: { icon: CirclePlus, text: '未出题', color: '#909399' },
  QUEUED: { icon: Loading, text: '排队中', color: '#e6a23c' },
  PROCESSING: { icon: Loading, text: '出题中', color: '#e6a23c' },
  COMPLETED: { icon: CircleCheck, text: '已出题', color: '#67c23a' },
  FAILED: { icon: CircleClose, text: '失败', color: '#f56c6c' },
};

const handleDelete = async (kbId: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该知识库吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteKnowledgeBase(kbId);
    ElMessage.success('删除成功');
    kbList.value = kbList.value.filter(item => item.id !== kbId);
  } catch (e: any) {
    if (e === 'cancel' || e?.toString?.() === 'cancel') {
      return;
    }
    ElMessage.error('删除失败');
    console.error('删除失败:', e);
  }
}

const handleInterview = () => {
  router.push('/backend/interview');
}

const handleRowClick = (row: KnowledgeBaseVO) => {
  router.push(`/backend/knowledgeBase/detail/${row.id}`);
}

onMounted(async () => {
  loading.value = true;
  try {
    const res = await getKnowledgeBaseList();
    kbList.value = res || [];
  } catch (e) {
    console.error('获取知识库列表失败:', e);
    ElMessage.error('获取知识库列表失败');
  } finally {
    loading.value = false;
  }
})
</script>

<template>
  <div class="kb-header">
    <div class="header-left">
      <div class="title">
        <el-icon style="color: #626aef;"><Reading /></el-icon>
        <span>面试知识库</span>
      </div>
      <div class="sub-title">
        支持上传文档辅助面试出题
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

  <div class="kb-content">
    <el-table
        class="kb-table"
        :data="kbList"
        v-loading="loading"
        style="width: 100%;
               border-radius: 20px;"
        empty-text="暂无知识库"
        @row-click="handleRowClick"
    >
      <el-table-column label="知识库名称" min-width="250">
        <template #default="scope">
          <div class="name-with-icon">
            <el-icon><Document /></el-icon>
            <span>{{ scope.row.name }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="fileSize" label="文件大小" width="160">
        <template #default="scope">
          <span>{{ scope.row.fileSize }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="uploadTime" label="上传时间" width="180">
        <template #default="scope">
          <span>{{ scope.row.uploadTime }}</span>
        </template>
      </el-table-column>

      <el-table-column label="向量化状态" width="140" align="center">
        <template #default="scope">
          <template v-if="scope.row.vectorStatus">
            <div
                class="status-text-wrapper"
                :style="{ color: vectorStatusMap[scope.row.vectorStatus]?.color }"
            >
              <el-icon
                  :class="{ 'is-loading': scope.row.vectorStatus === 'PENDING' || scope.row.vectorStatus === 'PROCESSING' }"
              >
                <component :is="vectorStatusMap[scope.row.vectorStatus]?.icon" />
              </el-icon>
              <span>{{ vectorStatusMap[scope.row.vectorStatus]?.text || scope.row.vectorStatus }}</span>
            </div>
          </template>
          <template v-else>
            <span style="color: #909399;">未处理</span>
          </template>
        </template>
      </el-table-column>

      <el-table-column label="出题状态" width="120" align="center">
        <template #default="scope">
          <div
              class="status-text-wrapper"
              :style="{ color: questionGenStatusMap[scope.row.questionGenStatus]?.color }"
          >
            <el-icon
                :class="{ 'is-loading': scope.row.questionGenStatus === 'QUEUED' || scope.row.questionGenStatus === 'PROCESSING' }"
            >
              <component :is="questionGenStatusMap[scope.row.questionGenStatus]?.icon" />
            </el-icon>
            <span>{{ questionGenStatusMap[scope.row.questionGenStatus]?.text || scope.row.questionGenStatus }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="scope">
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
</template>

<style scoped>
.name-with-icon {
  display: flex;
  align-items: center;
  gap: 6px;
}
.name-with-icon .el-icon {
  color: #909399;
}
.kb-header {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 20px;
  display: flex;
  padding: 20px;
  justify-content: space-between;
  background-color: #ffffff;
  border-radius: 20px;
}
.header-left {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.header-right {
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
.sub-title {
  color: #909399;
  font-size: 14px;
}
.kb-table {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
:deep(.kb-table .el-table__body .el-table__row) {
  cursor: pointer;
}
:deep(.kb-table th.el-table__cell) {
  color: #333;
  font-weight: 600;
  background-color: #ffffff;
}
:deep(.kb-table .el-table__body td.el-table__cell) {
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
.is-loading {
  animation: rotating 2s linear infinite;
}
@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>