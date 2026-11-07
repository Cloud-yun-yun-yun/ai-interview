<script setup lang="ts">
import {Monitor, Promotion, User} from "@element-plus/icons-vue";
</script>

<template>

  <div class="exam-container">
    <div class="exam-header">
      <div class="header-title">
        <div class="title-icon">
          <el-icon><Monitor/></el-icon>
        </div>
        <span class="title-text">模拟面试</span>
      </div>
      <span>认真回答问题展示你的实力</span>
    </div>
    <div class="exam-progress">
      <div>题目 1/6</div>
      <el-progress
          :percentage=100
          :show-text="false"
          color="#626aef"
          :stroke-width="8"
      />
    </div>
    <div class="exam-content">
      <div class="content-question">
        <div class="in-content">
          <div class="normal-image"><el-icon><User /></el-icon></div>
          <div class="in-message">
            <div class="message-header">
              <span>面试官</span>
              <el-tag>JVM</el-tag>
            </div>
            <span class="in-message-content">请谈谈你对Java虚拟机（JVM）内存模型的理解，以及常见的垃圾回收算法有哪些？</span>
          </div>
        </div>
        <div class="user-content">
          <span class="user-message-content">JVM允许Java字节码，GC分多种算法回收对象</span>
          <div class="normal-image"><el-icon><User /></el-icon></div>
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
          ></el-input>
          <el-button type="primary" class="submit-btn" :icon="Promotion">提交</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
.in-content{
  display: flex;
  gap:6px;
}
.normal-image{
  width:30px;
  height:30px;
  background-color: #7b82f3;
  color: #ffffff;
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