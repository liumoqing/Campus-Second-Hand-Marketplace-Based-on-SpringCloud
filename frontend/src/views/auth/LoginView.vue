<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captcha = ref<{ captchaKey: string; imageBase64: string } | null>(null)

const form = reactive({
  account: '',
  password: '',
  captchaKey: '',
  captchaCode: ''
})

const rules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaKey = captcha.value.captchaKey
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await user.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    form.captchaCode = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="brand">晴川二手市场</h1>
      <p class="slogan">让闲置流动起来</p>

      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="submit">
        <el-form-item prop="account">
          <el-input v-model="form.account" size="large" placeholder="用户名 / 邮箱" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="密码"
            prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" size="large" placeholder="验证码" maxlength="6" />
            <img v-if="captcha" :src="captcha.imageBase64" alt="验证码" class="captcha-img" @click="refreshCaptcha" />
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">登录</el-button>
      </el-form>

      <div class="links">
        <el-button link @click="router.push('/reset')">忘记密码？</el-button>
        <span class="mall-sub">还没有账号？</span>
        <el-button link type="primary" @click="router.push('/register')">立即注册</el-button>
      </div>
      <div class="mall-sub tip">演示账号：alice / bob（密码 Qingchuan@2026）</div>
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
  width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 28px 24px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}
.brand {
  font-size: 22px;
  margin: 0;
  color: var(--mall-primary);
  text-align: center;
}
.slogan {
  text-align: center;
  font-size: 12px;
  color: var(--mall-text-3);
  margin: 6px 0 22px;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  height: 40px;
  width: 110px;
  cursor: pointer;
  border-radius: 6px;
  border: 1px solid var(--mall-border);
  object-fit: cover;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.links {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 13px;
}
.tip {
  text-align: center;
  margin-top: 8px;
  font-size: 12px;
}
</style>
