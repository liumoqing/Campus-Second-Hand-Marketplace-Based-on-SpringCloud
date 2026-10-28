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
        <a href="/" class="logo" @click.prevent="refreshHome">晴川二手市场</a>

        <div class="search">
          <el-input
            v-model="keyword"
            placeholder="搜索你想要的二手好物"
            clearable
            @keyup.enter="search"
          >
            <template #append>
              <el-button :icon="'Search'" @click="search" />
            </template>
          </el-input>
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
  background: var(--mall-bg);
}
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--mall-border);
}
.header-inner {
  max-width: 1440px;
  margin: 0 auto;
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--mall-primary);
  white-space: nowrap;
}
.search {
  flex: 1;
  max-width: 520px;
  margin: 0 auto;
}
.right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}
.icon-btn {
  display: flex;
  gap: 4px;
  color: var(--mall-text-2);
}
.me {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.me-name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main {
  min-height: calc(100vh - 64px);
}
.fab {
  position: fixed;
  right: 32px;
  bottom: 40px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: none;
  background: var(--mall-primary);
  color: #fff;
  font-size: 26px;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(255, 107, 53, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
}
.fab:hover {
  filter: brightness(1.05);
}
</style>
