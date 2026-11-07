<script setup lang="ts">

import AppMain from "../components/AppMain.vue";
import Sidebar from "../components/Sidebar.vue";
</script>
<template>
  <div class="common-layout">
    <el-container>
      <el-aside class="aside-layout">
        <Sidebar/>
      </el-aside>
      <el-container class="main-layout">
        <el-main class="main-content">
          <AppMain/>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>
<style scoped>
.common-layout {
  height: 100%;
}
.el-container {
  height: 100%;
}
.aside-layout {
  width: 230px;
  height: 100%;
  background-color: #fff;
  border-right: none;
}
.main-layout {
  background-color: #f5f7fa;
  height: 100%;
}
.main-content {
  padding: 20px;
}
</style>
