<script setup lang="ts">
import { ref } from "vue"
import {User, Lock} from "@element-plus/icons-vue";
import { useRouter } from 'vue-router'
import {type RegisterForm, register} from '@/api/login'
import {ElMessage} from "element-plus";
import type {AxiosResponse} from "axios";
import type {ApiResult as Result} from "@/utils/request.ts";
const router = useRouter()
const registerForm = ref<RegisterForm>({
  username: '',
  password: '',
  verifyPassword:''
})
const handleRegister = async ()=>{
  console.log(registerForm.value)
  if(registerForm.value.username===null
      ||registerForm.value.password===null
        ||registerForm.value.verifyPassword===null){
    ElMessage.error('必填项为空')
    return
  }
  const res:AxiosResponse<Result> = await register(registerForm.value)
  if(res.data.code!=200){
    ElMessage.error('注册失败，稍后重试')
    return
  }
  router.push('/backend')
}
const goToLogin = ()=>{
  console.log("去登录")
  router.push('/login')
}
</script>

<template>
  <div class="total">
    <div class="register-box">
      <h2 class="title">注册账号</h2>

      <el-form :model="registerForm">
        <el-form-item>
          <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item>
          <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              size="large"
              :prefix-icon="Lock"
              @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-form-item>
          <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="确认密码"
              show-password
              size="large"
              :prefix-icon="Lock"
              @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-button
            color="#626aef"
            class="register-btn"
            size="large"
            @click="handleRegister"
        >
          注册
        </el-button>
        <div class="box-under">
          <span class="login-text" @click="goToLogin">返回登录</span>
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
.register-box {
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

.register-btn {
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
.login-text {
  text-align: right; /*文字靠右对齐 */
  color: #909399;
  font-size: 14px;
  cursor: pointer;   /* 鼠标放上去变成小手 */
  transition: color 0.3s;
}
.register-box:hover {
  color: #409EFF;
}
.login-text:hover {
  color: #409EFF;
}
</style>