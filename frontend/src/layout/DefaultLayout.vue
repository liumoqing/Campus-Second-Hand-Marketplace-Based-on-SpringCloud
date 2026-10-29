<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const route = useRoute()
const user = useUserStore()
const chat = useChatStore()

const keyword = ref((route.query.keyword as string) || '')

const unreadDot = computed(() => chat.unreadTotal + chat.noticeUnread)

function search() {
  router.push({ name: 'home', query: { ...route.query, keyword: keyword.value || undefined } })
}

async function goPublish() {
  if (!user.isLogin) {
    router.push({ path: '/login', query: { redirect: '/publish' } })
    return
  }
  if (!user.canPublish) {
    const until = user.publishBanUntil ? `，解封时间 ${user.publishBanUntil}` : ''
    ElMessageBox.alert(`您因违反用户规则暂时无法上架商品${until}`, '无法上架商品', {
      confirmButtonText: '我知道了',
      type: 'warning'
    })
    return
  }
  router.push('/publish')
}

function refreshHome() {
  // Logo 是“回首页并整页刷新”，不是普通 SPA 路由跳转
  if (route.fullPath === '/') window.location.reload()
  else window.location.assign('/')
}

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    user.logout().finally(() => router.push('/'))
    return
  }
  router.push(cmd)
}

onMounted(async () => {
  if (user.isLogin) {
    chat.connect()
    try {
      await user.fetchMe()
      await chat.refreshNoticeUnread()
    } catch {
      /* 静默 */
    }
  }
})
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="header-inner">
        <a href="/" class="logo" @click.prevent="refreshHome">
          <span class="logo-mark">
            <img src="/brand/qingchuan-logo.png" alt="武汉晴川学院校徽" />
          </span>
          <span class="logo-text">晴川二手市场</span>
        </a>

        <div class="search">
          <el-input
            v-model="keyword"
            class="search-input"
            placeholder="搜索你想要的二手好物"
            clearable
            @keyup.enter="search"
          />
          <button type="button" class="search-submit" aria-label="搜索" @click="search">
            <el-icon><Search /></el-icon>
          </button>
        </div>

        <div class="right">
          <el-badge :value="unreadDot" :hidden="unreadDot === 0" :max="99">
            <el-button link class="icon-btn" @click="router.push('/chat')">
              <el-icon><ChatDotSquare /></el-icon>
              消息
            </el-button>
          </el-badge>

          <template v-if="user.isLogin">
            <el-dropdown @command="onCommand">
              <span class="me">
                <el-avatar :size="30" :src="user.profile?.avatar || undefined">
                  {{ user.nickname.slice(0, 1) }}
                </el-avatar>
                <span class="me-name">{{ user.nickname }}</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/mine">我的主页</el-dropdown-item>
                  <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                  <el-dropdown-item command="/mine/favorite">我的收藏</el-dropdown-item>
                  <el-dropdown-item v-if="user.isAdmin" command="/admin/users">管理后台</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" plain size="small" @click="router.push('/login')">登录</el-button>
            <el-button size="small" @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main">
      <router-view />
    </main>

    <button class="fab" title="发布闲置" @click="goPublish">
      <el-icon><Plus /></el-icon>
    </button>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: transparent;
}
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 76px;
  background: rgba(250, 248, 243, 0.86);
  border-bottom: 1px solid var(--mall-border);
  backdrop-filter: blur(20px) saturate(150%);
  box-shadow: 0 6px 24px rgba(64, 50, 35, 0.035);
}
.header-inner {
  max-width: 1520px;
  margin: 0 auto;
  height: 76px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  gap: 28px;
}
.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--mall-text);
  white-space: nowrap;
}
.logo-mark {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex: 0 0 40px;
  border: 1px solid rgba(24, 62, 142, 0.12);
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 7px 18px rgba(24, 62, 142, 0.12);
}
.logo-mark img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.045em;
}
.search {
  flex: 1;
  max-width: 620px;
  height: 46px;
  margin: 0 auto;
  padding: 4px 5px 4px 15px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--mall-border);
  border-radius: 15px;
  background: rgba(255, 253, 249, 0.9);
  box-shadow: 0 8px 24px rgba(72, 55, 37, 0.04);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.search:focus-within {
  border-color: rgba(242, 107, 63, 0.38);
  box-shadow: 0 0 0 4px rgba(242, 107, 63, 0.08), 0 8px 24px rgba(72, 55, 37, 0.05);
}
.search-input {
  flex: 1;
  min-width: 0;
}
.search :deep(.el-input__wrapper) {
  min-height: 36px;
  padding: 0;
  border-radius: 10px;
  background: transparent;
  box-shadow: none !important;
}
.search-submit {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(135deg, #f47b50 0%, var(--mall-primary) 100%);
  box-shadow: 0 6px 14px rgba(242, 107, 63, 0.2);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.search-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 9px 18px rgba(242, 107, 63, 0.28);
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.icon-btn {
  display: flex;
  gap: 6px;
  padding: 9px 13px;
  border-radius: 12px;
  color: var(--mall-text-2);
  background: rgba(255, 253, 249, 0.72);
  transition: color 0.18s ease, background 0.18s ease;
}
.icon-btn:hover {
  color: var(--mall-primary);
  background: var(--mall-primary-light);
}
.me {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  outline: none;
  transition: background 0.18s ease;
}
.me:hover {
  background: rgba(255, 253, 249, 0.86);
}
.me-name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main {
  min-height: calc(100vh - 76px);
}
.fab {
  position: fixed;
  right: 34px;
  bottom: 36px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(145deg, #f88962 0%, var(--mall-primary) 56%, var(--mall-primary-deep) 100%);
  color: #fff;
  font-size: 26px;
  cursor: pointer;
  box-shadow: 0 16px 30px rgba(217, 79, 39, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.fab:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 20px 38px rgba(217, 79, 39, 0.36);
}

@media (max-width: 900px) {
  .header-inner {
    gap: 14px;
    padding: 0 16px;
  }
  .logo-text {
    display: none;
  }
  .me-name {
    display: none;
  }
}
</style>
