<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { authApi, productApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const uploading = ref(false)
const captcha = ref<{ captchaKey: string; imageBase64: string } | null>(null)
const avatarUrl = ref('')

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  captchaKey: '',
  captchaCode: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^\w{4,20}$/, message: '4~20 位字母、数字或下划线', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/, message: '8~20 位且包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (_r: any, v: string, cb: any) =>
        v !== form.password ? cb(new Error('两次输入的密码不一致')) : cb(),
      trigger: 'blur'
    }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaKey = captcha.value.captchaKey
}

function pickAvatar() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/png,image/jpeg,image/webp'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.error('头像不能超过 2MB')
      return
    }
    uploading.value = true
    try {
      const r = await authApi.uploadAvatar(file)
      avatarUrl.value = r.url
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

async function submit() {
  if (!avatarUrl.value) {
    ElMessage.warning('请先上传头像')
    return
  }
  await formRef.value?.validate()
  loading.value = true
  try {
    await user.register({
      avatar: avatarUrl.value,
      username: form.username,
      email: form.email,
      password: form.password,
      confirmPassword: form.confirmPassword,
      nickname: form.nickname || undefined,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    ElMessage.success('注册成功，请登录')
    router.replace({ path: '/login', query: { account: form.username } })
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
      <h1 class="brand">注册晴川二手市场</h1>

      <div class="avatar-block">
        <div class="avatar" @click="pickAvatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
          <span v-else>{{ uploading ? '上传中…' : '上传头像' }}</span>
        </div>
        <div class="mall-sub">头像必填，点击上传（≤2MB）</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4~20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="昵称（选填）">
          <el-input v-model="form.nickname" placeholder="不填则使用用户名" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于登录与找回密码" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8~20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再输入一次密码" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="验证码" maxlength="6" />
            <img v-if="captcha" :src="captcha.imageBase64" alt="验证码" class="captcha-img" @click="refreshCaptcha" />
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">注册</el-button>
      </el-form>

      <div class="links">
        <span class="mall-sub">已有账号？</span>
        <el-button link type="primary" @click="router.push('/login')">去登录</el-button>
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
  padding: 32px 0;
}
.auth-card {
  width: 440px;
  background: #fff;
  border-radius: 12px;
  padding: 28px 28px 20px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}
.brand {
  font-size: 20px;
  margin: 0 0 18px;
  text-align: center;
  color: var(--mall-primary);
}
.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
}
.avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  border: 1px dashed var(--mall-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  font-size: 12px;
  color: var(--mall-text-3);
  background: #fafbfc;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
}
.submit {
  width: 100%;
}
.links {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
}
</style>
