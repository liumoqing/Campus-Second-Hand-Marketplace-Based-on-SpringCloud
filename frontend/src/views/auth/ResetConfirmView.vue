<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const form = reactive({
  email: (route.query.email as string) || '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

async function submit() {
  if (!form.email) {
    ElMessage.warning('请填写邮箱')
    return
  }
  if (!/^\d{6}$/.test(form.code)) {
    ElMessage.warning('验证码为 6 位数字')
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(form.newPassword)) {
    ElMessage.warning('新密码需 8~20 位且包含字母和数字')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword(form)
    ElMessage.success('密码已重置，请用新密码登录')
    router.replace({ path: '/login' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="brand">设置新密码</h1>

      <el-form label-position="top">
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="注册邮箱" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位数字验证码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="8~20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再输入一次" />
        </el-form-item>
      </el-form>

      <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">重置密码</el-button>

      <div class="links">
        <el-button link @click="router.push('/reset')">‹ 重新获取验证码</el-button>
        <el-button link @click="router.push('/login')">返回登录</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff3ec 0%, #f7f8fa 60%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.auth-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 30px 28px 22px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}
.brand {
  font-size: 20px;
  margin: 0 0 16px;
  text-align: center;
  color: var(--mall-primary);
}
.submit {
  width: 100%;
}
.links {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
}
</style>
