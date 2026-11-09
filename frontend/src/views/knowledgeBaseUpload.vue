<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type UploadFile} from 'element-plus'
import {Document, Loading, UploadFilled} from '@element-plus/icons-vue'
import { uploadKnowledgeBase } from '@/api/business'
const isUploading = ref(false)
const hasUploaded = ref(false)
const fileName = ref('')
const resetUpload = () => {
  hasUploaded.value = false
  fileName.value = ''
}
const handleChange = async (uploadFile: UploadFile) => {
  console.log('被点击了')
  const file = uploadFile.raw
  if (!file) return
  try{
    isUploading.value = true
    fileName.value = file.name
    await uploadKnowledgeBase(file)
    ElMessage.success('上传成功！')
    hasUploaded.value = true
  }catch (error: any) {
    ElMessage.error('上传失败，请检查文件或网络')
  }finally {
    isUploading.value = false
  }
  return false
}
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div class="page-title">知识库上传</div>
      <div class="page-subtitle">上传知识库文件，后续可依据知识库进行智能面试</div>
    </div>

    <div class="upload-container">
      <el-upload
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleChange"
      >
        <div v-if="isUploading" class="upload-status-box">
          <el-icon class="is-loading" :size="50"><Loading /></el-icon>
          <p>正在解析您的简历，请稍候...</p>
        </div>

        <div v-else-if="hasUploaded" class="upload-success-box">
          <el-icon :size="60" color="#67C23A"><Document /></el-icon>
          <div class="file-info">
            <span class="file-name">{{ fileName }}</span>
            <span class="file-desc">已上传成功</span>
          </div>
          <el-button type="info" plain @click.stop="resetUpload">重新上传</el-button>
        </div>

        <div v-else class="default-upload-box">
          <el-icon class="el-icon--upload" style="color: #626aef;"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽简历至此，或 <span class="highlight">点击上传</span>
            <div class="sub-text">允许上传"pdf", "md", "markdown", "txt", "html", "doc"等文本文件<br>
              文件大小不超过 10MB
            </div>
          </div>
        </div>
      </el-upload>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
  height: 100%;
  background-color: #f7f9fc;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
}
.page-top {
  padding-top: 100px;
  padding-bottom: 50px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 36px;
  color: #1a1a1a;
  font-weight: 600;
  letter-spacing: 1px; /* 增加一点字间距，显高级 */
  line-height: 1.4;
}

.page-subtitle {
  font-size: 16px;
  color: #8c92a4;
  font-weight: 400;
  letter-spacing: 0.5px;
}

.upload-container {
  width: 520px;
  height: 320px;
  margin: 0 auto;
}


/* 覆盖 el-upload 默认样式，让它撑满区域 */
:deep(.el-upload-dragger) {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-radius: 12px; /* 圆角更好看 */
  background-color: #ffffff; /* 非常浅的灰色背景 */
  border: 2px dashed #dcdfe6; /* 虚线边框 */
  transition: all 0.3s ease;
}

/* 鼠标悬浮时的交互效果 */
:deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background-color: #f0f7ff;
}

/* 图标样式 */
.el-icon--upload {
  font-size: 60px;
  color: #409eff;
  margin-bottom: 16px;
}

/* 主文字样式 */
.el-upload__text {
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
  margin-top: 16px;
  text-align: center;
}

/* 点击上传 这个词高亮成主题色 */
.highlight {
  color: #626aef;
  font-weight: 500;
  cursor: pointer;
}

.sub-text {
  font-size: 13px; /* 缩小提示文字 */
  color: #a8abb2;
  margin-top: 8px;
}
.upload-status-box,
.upload-success-box,
.default-upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px; /* 元素间距 */
}

/* 阻止上传成功后的多余点击 */
.upload-success-box {
  cursor: default;
}
</style>