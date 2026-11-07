<script setup lang="ts">
import {ArrowLeft, DocumentAdd, Files, List, Monitor, Reading, Upload} from "@element-plus/icons-vue";

import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const router = useRouter()

const backendRoute = router.options.routes.find(r => r.path === '/backend')
const allMenus = backendRoute ? backendRoute.children : []
const resumeMenus = allMenus.slice(0, 4)
const knowledgeMenus = allMenus.slice(4,6)

const getIcon = (iconName: string) => {
  const icons = { Reading, DocumentAdd, Files, List, Monitor, Upload }
  return icons[iconName] || Files // 如果找不到就默认返回 Files
}

const handleMenuClick = (path: string) => {
  router.push(path)
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    router.push('/login')
    localStorage.removeItem('token')
  } catch {
  }
}
</script>

<template>
  <el-aside width="230px" class="my-aside">
    <el-scrollbar >
      <el-menu
          class="my-menu"
          background-color="#fff"
          active-text-color="#626aef"
          :default-active="$route.path"
          text-color="#303133"
      >
        <div class="menu-group-title">面试准备</div>
          <el-menu-item
              v-for="item in resumeMenus"
              :key="item.path"
              :index="item.path"
              @click="handleMenuClick(item.path)"
              style="height: 60px; line-height: 60px; font-size: 16px;"
          >
            <el-icon class="sidebar-icon">
              <component :is="getIcon(item.meta?.icon as string)" />
            </el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>


        <div class="menu-group-title">知识库</div>
          <el-menu-item
              v-for="item in knowledgeMenus"
              :key="item.path"
              :index="item.path"
              @click="handleMenuClick(item.path)"
              style="height: 60px; line-height: 60px; font-size: 16px;"
          >
            <el-icon class="sidebar-icon">
              <component :is="getIcon(item.meta?.icon as string)" />
            </el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>
      </el-menu>
    </el-scrollbar>
    <div class="footer">
      <el-button type="danger" link :icon="ArrowLeft" @click="handleLogout">退出登录</el-button>
    </div>
  </el-aside>
</template>

<style scoped>
.my-aside {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.menu-group-title {
  padding: 20px 20px 8px;
  font-size: 15px;
  color: #626aef;
  font-weight: bold;
}
.sidebar-icon {
  background-color: #f0f2ff;
  padding: 2px;
  border-radius: 4px;
}
.my.my-menu {
  border-right: none;
}

/* 给菜单项设置基础的内边距和外边距，为了让圆角背景好看，通常需要留点边距 */
.my-menu :deep(.el-menu-item) {
  margin: 4px 12px; /* 上下留白4px，左右留白12px，这样高亮背景就不会贴着边缘 */
  border-radius: 8px; /* 设置圆角，数值可根据喜好调整 */
  height: 50px; /* 稍微加高一点，看起来更大气 */
  line-height: 50px;
}

/* 重点：设置选中时的背景色 */
.my-menu :deep(.el-menu-item.is-active) {
  background-color: #f0f2ff; /* 这里换成你第二张图那种浅紫色背景色 */
  color: #626aef; /* 文字和图标颜色（其实 active-text-color 已经帮你搞定了文字） */
  font-weight: bold; /* 选中时加粗文字 */
}

.my-menu :deep(.el-menu-item:hover) {
  background-color: #f5f7fa; /* 鼠标移上去的浅灰色背景 */
}

.my-aside :deep(.el-scrollbar__wrap) {
}
.footer{
  margin-bottom: 10px;

}
</style>