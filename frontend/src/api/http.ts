import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { R } from '@/types'

// 浏览器默认走 Vite 同源代理，避免本地浏览器/隐私网络策略导致跨域 Network Error。
// Vite 会把 /api 代理到网关 4399，并去掉 /api 前缀。
// 需要直连时仍可通过 VITE_API_BASE 覆盖，例如 http://127.0.0.1:4399
export const API_BASE = (import.meta.env.VITE_API_BASE as string) || '/api'

const http = axios.create({ baseURL: API_BASE, timeout: 15000 })

export const TOKEN_KEY = 'mall_access_token'
export const REFRESH_KEY = 'mall_refresh_token'
export const USER_KEY = 'mall_user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(access?: string, refresh?: string) {
  if (access) localStorage.setItem(TOKEN_KEY, access)
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

http.interceptors.request.use((cfg) => {
  const token = getToken()
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

let refreshing: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return false
  try {
    const r = await axios.post<R<{ accessToken: string; refreshToken: string }>>(
      `${API_BASE}/auth/refresh`,
      { refreshToken: refresh }
    )
    if (r.data?.code === 0 && r.data.data?.accessToken) {
      setToken(r.data.data.accessToken, r.data.data.refreshToken)
      return true
    }
  } catch {
    /* ignore */
  }
  return false
}

function toLogin() {
  clearAuth()
  const redirect = encodeURIComponent(location.pathname + location.search)
  if (!location.pathname.startsWith('/login')) location.replace(`/login?redirect=${redirect}`)
}

/** 封禁/上架受限这类"治理类"错误要弹窗，不能只 toast */
function handleGovernance(code: number, message: string, data: any): boolean {
  if (code === 20003) {
    const until = data?.banUntil ? `，解封时间 ${data.banUntil}` : ''
    ElMessageBox.alert(`您因违反用户规则已被封禁${until}`, '账号已被封禁', {
      confirmButtonText: '我知道了',
      type: 'error'
    }).finally(() => toLogin())
    return true
  }
  if (code === 30009) {
    const until = data?.publishBanUntil ? `，解封时间 ${data.publishBanUntil}` : ''
    ElMessageBox.alert(`您因违反用户规则暂时无法上架商品${until}`, '无法上架商品', {
      confirmButtonText: '我知道了',
      type: 'warning'
    })
    return true
  }
  return false
}

export async function request<T>(cfg: AxiosRequestConfig, retried = false): Promise<T> {
  try {
    const res = await http.request<R<T>>(cfg)
    const body = res.data
    if (body.code === 0) return body.data
    if (handleGovernance(body.code, body.message, body.data)) throw new Error(body.message)
    if (body.code === 10002 && !retried) {
      refreshing = refreshing ?? tryRefresh().finally(() => (refreshing = null))
      const ok = await refreshing
      if (ok) return request<T>(cfg, true)
      toLogin()
      throw new Error(body.message)
    }
    ElMessage.error(body.message || '请求失败')
    throw new Error(body.message)
  } catch (e: any) {
    if (e?.response?.status === 401) {
      if (!retried) {
        refreshing = refreshing ?? tryRefresh().finally(() => (refreshing = null))
        const ok = await refreshing
        if (ok) return request<T>(cfg, true)
      }
      toLogin()
      throw new Error('登录已过期')
    }
    if (e?.response?.data?.code) {
      const b = e.response.data
      if (handleGovernance(b.code, b.message, b.data)) throw new Error(b.message)
      ElMessage.error(b.message || '请求失败')
    } else if (e?.response) {
      // 后端没返回业务体（网关 502/504、反代错误页等）：把状态码和地址露出来，方便定位
      ElMessage.error(`接口异常 ${e.response.status} ${cfg.method?.toUpperCase()} ${cfg.url}`)
    } else if (e?.code === 'ECONNABORTED') {
      ElMessage.error(`请求超时 ${cfg.method?.toUpperCase()} ${cfg.url}`)
    }
    throw e
  }
}

export const get = <T>(url: string, params?: any) => request<T>({ url, method: 'get', params })
export const post = <T>(url: string, data?: any) => request<T>({ url, method: 'post', data })
export const put = <T>(url: string, data?: any, params?: any) =>
  request<T>({ url, method: 'put', data, params })
export const del = <T>(url: string, params?: any) => request<T>({ url, method: 'delete', params })

export default http
