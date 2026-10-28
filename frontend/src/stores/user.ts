import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, userApi } from '@/api'
import { clearAuth, getToken, setToken, USER_KEY } from '@/api/http'
import type { UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref<UserInfo | null>(readCached())

  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(
    () => profile.value?.role === 'ADMIN' || profile.value?.role === 'SUPER_ADMIN'
  )
  const isSuperAdmin = computed(() => profile.value?.role === 'SUPER_ADMIN')
  const canPublish = computed(() => profile.value?.canPublish !== false)
  const publishBanUntil = computed(() => profile.value?.publishBanUntil ?? null)
  const nickname = computed(() => profile.value?.nickname || profile.value?.username || '游客')

  function readCached(): UserInfo | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? (JSON.parse(raw) as UserInfo) : null
    } catch {
      return null
    }
  }

  function cache(u: UserInfo | null) {
    profile.value = u
    if (u) localStorage.setItem(USER_KEY, JSON.stringify(u))
    else localStorage.removeItem(USER_KEY)
  }

  async function login(form: { account: string; password: string; captchaKey: string; captchaCode: string }) {
    const r = await authApi.login(form)
    setToken(r.accessToken, r.refreshToken)
    token.value = r.accessToken
    cache(r.userInfo)
    return r
  }

  async function register(form: Parameters<typeof authApi.register>[0]) {
    return authApi.register(form)
  }

  async function fetchMe() {
    if (!token.value) return null
    const me = await authApi.me()
    cache(me)
    return me
  }

  async function updateProfile(data: Partial<UserInfo>) {
    await userApi.updateProfile(data)
    await fetchMe()
  }

  async function logout() {
    const refresh = localStorage.getItem('mall_refresh_token') || undefined
    try {
      await authApi.logout(refresh)
    } catch {
      /* 退出失败也要清本地 */
    }
    clearAuth()
    token.value = ''
    cache(null)
  }

  /** 被踢下线/封禁时调用：清本地但不打后端 */
  function localLogout() {
    clearAuth()
    token.value = ''
    cache(null)
  }

  return {
    token,
    profile,
    isLogin,
    isAdmin,
    isSuperAdmin,
    canPublish,
    publishBanUntil,
    nickname,
    login,
    register,
    fetchMe,
    updateProfile,
    logout,
    localLogout,
    cache
  }
})
