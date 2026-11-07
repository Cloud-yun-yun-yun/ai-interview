<script setup lang="ts">
import { ref } from "vue"
import {User, Lock} from "@element-plus/icons-vue";
import { useRouter, useRoute } from 'vue-router'
import {type LoginForm, login} from '@/api/login'

import {ElMessage} from "element-plus";

const router = useRouter()
const route = useRoute()
const loginForm = ref<LoginForm>({
  username: '',
  password: ''
})
const handleLogin = async ()=>{
  console.log(loginForm.value)
  if(loginForm.value.username===null||loginForm.value.password===null){
    ElMessage.error('必填项为空')
    return
  }
  const token = await login(loginForm.value)
  console.log(token)
  if(!token){
    ElMessage.error('登录失败')
    return
  }else{
    console.log(token)
    localStorage.setItem('token',String(token))
  }
  ElMessage.success('登录成功')
  const redirect = route.query.redirect as string
  router.push(redirect || '/backend/upload')
}
const goToRegister = ()=>{
  console.log("去注册")
  router.push('/register')
}
</script>

<template>
  <div class="total">
    <div class="login-box">
      <h2 class="title">登录账号</h2>

      <el-form :model="loginForm">
        <el-form-item>
          <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item>
          <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              size="large"
              :prefix-icon="Lock"
              @keyup.enter="handleLogin"
          /> <!--按下enter触发handleLogin函数-->
        </el-form-item>

        <el-button
            color="#626aef"
            class="login-btn"
            size="large"
            @click="handleLogin"
        >
          登 录
        </el-button>
        <div class="box-under">
          <span class="register-text" @click="goToRegister">注册账号</span>
        </div>
      </el-form>
    </div>

  </div>
</template>

<style scoped>
.total {
  min-height: 100vh;
  background-color: #f7f9fc;
  display: flex;
  align-items: center;
  justify-content: center;
}

/*用 div 代替 el-card，去掉自带的内边距干扰 */
.login-box {
  width: 420px;
  background: #ffffff;
  padding: 40px 40px 50px 40px;
  border-radius: 20px; /* 圆角 */
}

.title {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
  font-size: 22px;
  font-weight: 600;
}

.login-btn {
  width: 100%;
  margin-top: 10px;
  height: 45px;
  font-size: 16px;
  border-radius: 10px; /* 按钮圆角 */
}

/*去掉 Element Plus 输入框默认的底部 margin，自己控制间距 */
:deep(.el-form-item) {
  margin-bottom: 24px;
}
.box-under{
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-right: 5px;
}
.register-text {
  text-align: right; /*文字靠右对齐 */
  color: #909399;
  font-size: 14px;
  cursor: pointer;   /* 鼠标放上去变成小手 */
  transition: color 0.3s;
}
.register-box:hover {
  color: #409EFF;
}
.register-text:hover {
  color: #409EFF;
}
</style>