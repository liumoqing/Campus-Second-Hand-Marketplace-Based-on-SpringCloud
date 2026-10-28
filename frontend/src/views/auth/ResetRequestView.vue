<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const router = useRouter()
const email = ref('')
const loading = ref(false)
const sent = ref(false)
const devCode = ref('')

async function submit() {
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value)) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  loading.value = true
  try {
    const r = await authApi.resetCode(email.value)
    sent.value = true
    devCode.value = r.devCode || ''
    ElMessage.success('如果该邮箱已注册，验证码已发送（10 分钟内有效）')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="brand">找回密码</h1>
      <ol class="steps mall-sub">
        <li>输入注册邮箱，获取 6 位验证码</li>
        <li>在下一步填写验证码与新密码</li>
      </ol>

      <el-input v-model="email" size="large" placeholder="注册邮箱" />
      <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
        {{ sent ? '重新发送验证码' : '获取验证码' }}
      </el-button>

      <el-alert
        v-if="devCode"
        type="success"
        :closable="false"
        show-icon
        class="tip"
        :title="`开发环境验证码：${devCode}`"
      />

      <div class="links">
        <el-button link @click="router.push('/login')">返回登录</el-button>
        <el-button v-if="sent" link type="primary" @click="router.push({ path: '/reset/confirm', query: { email } })">
          下一步：重置密码 ›
        </el-button>
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
  margin: 0 0 8px;
  text-align: center;
  color: var(--mall-primary);
}
.steps {
  font-size: 12px;
  padding-left: 18px;
  margin: 0 0 18px;
  line-height: 1.8;
}
.submit {
  width: 100%;
  margin-top: 14px;
}
.tip {
  margin-top: 14px;
}
.links {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
}
</style>
