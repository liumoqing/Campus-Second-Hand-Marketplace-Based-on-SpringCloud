import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { messageApi } from '@/api'
import { getToken } from '@/api/http'
import { useUserStore } from './user'
import type { ChatMessage, ConversationItem } from '@/types'

type WsStatus = 'idle' | 'connecting' | 'open' | 'closed'

/** 聊天：会话列表、消息缓存、未读、WebSocket 连接与重连 */
export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationItem[]>([])
  const messages = ref<Record<string, ChatMessage[]>>({})
  const unreadTotal = ref(0)
  const noticeUnread = ref(0)
  const peerOnlineMap = ref<Record<string, boolean>>({})
  const wsStatus = ref<WsStatus>('idle')

  let ws: WebSocket | null = null
  let retry = 0
  let timer: number | null = null
  let heartbeat: number | null = null
  const pending = new Map<string, (msg: ChatMessage) => void>()

  function wsUrl() {
    // 与 axios 一样直连网关（走 Vite 代理会偶发 500，见 api/http.ts 的说明）
    const base = (import.meta.env.VITE_WS_BASE as string) || 'ws://127.0.0.1:4399'
    return `${base.replace(/\/$/, '')}/ws/chat?token=${encodeURIComponent(getToken())}`
  }

  function connect() {
    if (!getToken() || wsStatus.value === 'open' || wsStatus.value === 'connecting') return
    wsStatus.value = 'connecting'
    ws = new WebSocket(wsUrl())
    ws.onopen = () => {
      wsStatus.value = 'open'
      retry = 0
      heartbeat = window.setInterval(() => send({ type: 'PING' }), 30000)
    }
    ws.onmessage = (ev) => onFrame(JSON.parse(ev.data))
    ws.onclose = () => {
      wsStatus.value = 'closed'
      if (heartbeat) window.clearInterval(heartbeat)
      scheduleReconnect()
    }
    ws.onerror = () => ws?.close()
  }

  function scheduleReconnect() {
    if (!getToken()) return
    retry += 1
    const delay = Math.min(1000 * Math.pow(2, Math.min(retry, 5)), 30000)
    if (timer) window.clearTimeout(timer)
    timer = window.setTimeout(connect, delay)
  }

  function send(frame: any) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(frame))
      return true
    }
    return false
  }

  function sendMessage(payload: {
    conversationId: string
    toId: string
    contentType: 'TEXT' | 'IMAGE'
    content: string
  }) {
    const clientMsgId = crypto.randomUUID()
    const ok = send({ type: 'CHAT', ...payload, clientMsgId })
    if (!ok) {
      // WS 断了：直接用 HTTP 兜底（后端同一套校验）
      return messageApi.send({ ...payload, clientMsgId }).then((m) => {
        pushMessage(m)
        return m
      })
    }
    return new Promise<ChatMessage>((resolve) => {
      pending.set(clientMsgId, resolve)
      window.setTimeout(() => pending.delete(clientMsgId), 10000)
    })
  }

  function pushMessage(m: ChatMessage) {
    const list = messages.value[m.conversationId] || []
    const exists = list.some((x) => x.msgId === m.msgId || (m.clientMsgId && x.clientMsgId === m.clientMsgId))
    if (!exists) {
      messages.value[m.conversationId] = [...list, m]
    }
  }

  function onFrame(frame: any) {
    switch (frame.type) {
      case 'PONG':
        break
      case 'ACK': {
        const cb = pending.get(frame.data.clientMsgId)
        if (cb) {
          pending.delete(frame.data.clientMsgId)
          cb({
            msgId: frame.data.msgId,
            createTime: frame.data.createTime,
            clientMsgId: frame.data.clientMsgId
          } as ChatMessage)
        }
        break
      }
      case 'CHAT':
      case 'CARD': {
        pushMessage(frame.data as ChatMessage)
        const conv = conversations.value.find((c) => c.conversationId === frame.data.conversationId)
        if (conv) {
          conv.lastMsg = frame.data.contentType === 'TEXT' ? frame.data.content : `[${frame.data.contentType === 'IMAGE' ? '图片' : '订单'}]`
          conv.lastTime = frame.data.createTime
          conv.unreadCount = (conv.unreadCount || 0) + 1
        }
        unreadTotal.value += 1
        break
      }
      case 'READ':
        unreadTotal.value = Number(frame.data.unreadTotal ?? 0)
        break
      case 'NOTICE':
        // 以服务端为准，避免已读后迟到的 NOTICE 帧把红点重新加回来
        refreshNoticeUnread().catch(() => {})
        break
      case 'KICKED': {
        const d = frame.data || {}
        const until = d.banUntil ? `，解封时间 ${d.banUntil}` : ''
        ElMessageBox.alert(
          d.reason === 'BANNED' ? `您因违反用户规则已被封禁${until}` : d.message || '账号已在其他设备登录',
          d.reason === 'BANNED' ? '账号已被封禁' : '下线提示',
          { confirmButtonText: '我知道了', type: 'error' }
        ).finally(() => {
          useUserStore().localLogout()
          location.replace('/login')
        })
        break
      }
      case 'ERROR':
        break
      default:
        break
    }
  }

  async function loadConversations(keyword?: string) {
    const page = await messageApi.conversations({ pageNum: 1, pageSize: 50, keyword })
    conversations.value = page.records
    unreadTotal.value = page.records.reduce((s, c) => s + (c.unreadCount || 0), 0)
    return page.records
  }

  async function loadHistory(conversationId: string, lastId?: string) {
    const r = await messageApi.history({ conversationId, lastId, size: 20 })
    const rows = [...r.records].reverse()
    if (lastId) {
      messages.value[conversationId] = [...rows, ...(messages.value[conversationId] || [])]
    } else {
      messages.value[conversationId] = rows
    }
    return r
  }

  async function markRead(conversationId: string, lastMsgId?: string) {
    if (wsStatus.value === 'open') {
      send({ type: 'READ', conversationId, lastMsgId })
    }
    const r = await messageApi.read({ conversationId, lastMsgId })
    unreadTotal.value = Number(r.unreadTotal ?? 0)
    const conv = conversations.value.find((c) => c.conversationId === conversationId)
    if (conv) conv.unreadCount = 0
  }

  async function refreshNoticeUnread() {
    const r = await messageApi.noticeUnreadCount()
    noticeUnread.value = Number(r.total ?? 0)
    return noticeUnread.value
  }

  function close() {
    if (timer) window.clearTimeout(timer)
    if (heartbeat) window.clearInterval(heartbeat)
    ws?.close()
    ws = null
    wsStatus.value = 'idle'
  }

  return {
    conversations,
    messages,
    unreadTotal,
    noticeUnread,
    peerOnlineMap,
    wsStatus,
    connect,
    close,
    sendMessage,
    loadConversations,
    loadHistory,
    markRead,
    refreshNoticeUnread,
    pushMessage
  }
})
