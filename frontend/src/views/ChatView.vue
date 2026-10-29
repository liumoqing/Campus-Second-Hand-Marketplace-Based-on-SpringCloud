<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { messageApi, productApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import type { ChatMessage, NoticeItem } from '@/types'

const route = useRoute()
const router = useRouter()
const chat = useChatStore()
const user = useUserStore()

const activeId = ref<string>((route.query.cid as string) || '')
const showNotice = ref(route.query.tab === 'notice')
const keyword = ref('')
const text = ref('')
const notices = ref<NoticeItem[]>([])
const listRef = ref<HTMLElement>()
const loadingHistory = ref(false)

const conversations = computed(() => chat.conversations)
const messages = computed(() => (activeId.value ? chat.messages[activeId.value] || [] : []))
const activeConv = computed(() => conversations.value.find((c) => c.conversationId === activeId.value))

const cardText: Record<string, string> = {
  ORDER_CREATED: '买家已下单，等待付款',
  ORDER_PAID: '买家已付款，请尽快发货',
  ORDER_SHIPPED: '卖家已发货，请注意查收',
  ORDER_RECEIVED: '买家已确认收货',
  ORDER_CANCELLED: '订单已取消',
  REFUND_APPLIED: '买家申请了退款',
  REFUND_DONE: '退款已完成'
}

function summary(c: any) {
  if (c.lastMsgType === 'IMAGE') return '[图片]'
  if (c.lastMsgType === 'PRODUCT') return '[商品]'
  if (c.lastMsgType === 'ORDER') return '[订单]'
  return c.lastMsg || ''
}

async function selectConversation(id: string) {
  activeId.value = id
  showNotice.value = false
  router.replace({ query: { cid: id } })
  await chat.loadHistory(id)
  const last = chat.messages[id]?.[chat.messages[id].length - 1]
  await chat.markRead(id, last?.msgId)
  scrollBottom()
}

async function loadNotices() {
  showNotice.value = true
  activeId.value = ''
  router.replace({ query: { tab: 'notice' } })
  const page = await messageApi.notices({ pageNum: 1, pageSize: 30 })
  // 进入系统通知页即清空全部通知未读，而不是只清当前这一页。
  await messageApi.read({ noticeIds: [] })
  notices.value = page.records.map((n) => ({ ...n, isRead: true }))
  await chat.refreshNoticeUnread()
}

async function send() {
  const content = text.value.trim()
  if (!content || !activeId.value || !activeConv.value) return
  text.value = ''
  const msg = await chat.sendMessage({
    conversationId: activeId.value,
    toId: activeConv.value.peerId,
    contentType: 'TEXT',
    content
  })
  if (msg && msg.msgId) {
    chat.pushMessage({
      ...(msg as ChatMessage),
      conversationId: activeId.value,
      fromId: user.profile?.id || '',
      toId: activeConv.value.peerId,
      contentType: 'TEXT',
      content,
      status: 'SENT',
      createTime: msg.createTime || new Date().toISOString()
    })
  }
  scrollBottom()
}

async function sendImage() {
  if (!activeId.value || !activeConv.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.error('图片不能超过 5MB')
      return
    }
    const r = await productApi.upload(file, 'chat')
    const msg = await chat.sendMessage({
      conversationId: activeId.value,
      toId: activeConv.value!.peerId,
      contentType: 'IMAGE',
      content: r.url
    })
    if (msg) {
      chat.pushMessage({
        ...(msg as ChatMessage),
        conversationId: activeId.value,
        fromId: user.profile?.id || '',
        toId: activeConv.value!.peerId,
        contentType: 'IMAGE',
        content: r.url,
        status: 'SENT',
        createTime: msg.createTime || new Date().toISOString()
      })
    }
    scrollBottom()
  }
  input.click()
}

async function loadMore() {
  if (!activeId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const first = messages.value[0]
    const r = await chat.loadHistory(activeId.value, first?.msgId)
    if (!r.hasMore) ElMessage.info('没有更早的消息了')
  } finally {
    loadingHistory.value = false
  }
}

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function openProduct(pid: string) {
  if (pid) router.push(`/product/${pid}`)
}

function openOrder(orderNo: string, refund = false) {
  router.push({
    path: '/orders',
    query: refund ? { tab: 'REFUND', role: 'sold' } : { orderNo }
  })
}

function orderCardTitle(message: ChatMessage) {
  return message.content?.productTitle || activeConv.value?.productTitle || '商品信息'
}

function orderCardCover(message: ChatMessage) {
  return message.content?.productCover || activeConv.value?.productCover || ''
}

watch(
  () => chat.messages[activeId.value]?.length,
  () => scrollBottom()
)

onMounted(async () => {
  chat.connect()
  await chat.loadConversations()
  if (activeId.value) selectConversation(activeId.value)
  else await loadNotices()
})
</script>

<template>
  <div class="chat mall-page">
    <aside class="conv-list">
      <div class="conv-search">
        <el-input v-model="keyword" placeholder="搜索会话" size="small" clearable @change="chat.loadConversations(keyword)" />
      </div>

      <div class="conv sys" :class="{ active: showNotice }" @click="loadNotices">
        <el-icon><Bell /></el-icon>
        <span class="conv-name">系统通知</span>
        <el-badge :value="chat.noticeUnread" :hidden="!chat.noticeUnread" :max="99" class="conv-badge" />
      </div>

      <div
        v-for="c in conversations"
        :key="c.conversationId"
        class="conv"
        :class="{ active: activeId === c.conversationId }"
        @click="selectConversation(c.conversationId)"
      >
        <div class="avatar-wrap">
          <el-avatar :size="38" :src="c.peerAvatar || undefined">{{ c.peerNickname?.slice(0, 1) }}</el-avatar>
          <span v-if="c.peerOnline" class="online-dot" />
        </div>
        <div class="conv-main">
          <div class="conv-top">
            <span class="conv-name">{{ c.peerNickname }}</span>
            <span class="conv-time">{{ (c.lastTime || '').slice(5, 16) }}</span>
          </div>
          <div class="conv-bottom">
            <span class="conv-last">{{ summary(c) }}</span>
            <el-badge v-if="c.unreadCount" :value="c.unreadCount" :max="99" />
          </div>
        </div>
      </div>
      <el-empty v-if="!conversations.length" description="还没有会话" :image-size="60" />
    </aside>

    <section class="panel">
      <template v-if="showNotice">
        <div class="panel-head">系统通知</div>
        <div class="notice-list">
          <div v-for="n in notices" :key="n.noticeId" class="notice" :class="{ unread: !n.isRead }">
            <div class="notice-top">
              <b>{{ n.title }}</b>
              <span class="mall-sub">{{ n.createTime }}</span>
            </div>
            <div class="notice-content">{{ n.content }}</div>
            <div v-if="n.bizType === 'ORDER' && n.bizId" class="notice-op">
              <el-button link type="primary" size="small" @click="openOrder(n.bizId, n.type === 'REFUND')">
                {{ n.type === 'REFUND' ? '去处理退款' : '查看订单' }}
              </el-button>
            </div>
            <div v-else-if="n.bizType === 'PRODUCT' && n.bizId" class="notice-op">
              <el-button link type="primary" size="small" @click="openProduct(n.bizId)">查看商品</el-button>
            </div>
          </div>
          <el-empty v-if="!notices.length" description="没有通知" />
        </div>
      </template>

      <template v-else-if="activeConv">
        <div class="panel-head">
          <span>{{ activeConv.peerNickname }}</span>
          <span v-if="activeConv.peerOnline" class="online-text">在线</span>
          <span class="mall-sub ws-status">{{ chat.wsStatus === 'open' ? '' : '连接中…' }}</span>
        </div>

        <div ref="listRef" class="msg-list">
          <div class="load-more">
            <el-button link size="small" :loading="loadingHistory" @click="loadMore">加载更早的消息</el-button>
          </div>

          <div
            v-for="m in messages"
            :key="m.msgId"
            class="msg"
            :class="{ mine: m.fromId === user.profile?.id }"
          >
            <div class="bubble">
              <!-- 文本 -->
              <template v-if="m.contentType === 'TEXT'">{{ m.content }}</template>
              <!-- 图片 -->
              <el-image
                v-else-if="m.contentType === 'IMAGE'"
                :src="m.content"
                :preview-src-list="[m.content]"
                fit="cover"
                class="img-bubble"
              />
              <!-- 商品卡片 -->
              <div v-else-if="m.contentType === 'PRODUCT'" class="card-bubble" @click="openProduct(m.content.productId || m.content.id)">
                <img v-if="m.content.coverUrl" :src="m.content.coverUrl" alt="" />
                <div class="cb-info">
                  <div class="cb-title">{{ m.content.title }}</div>
                  <div class="mall-price">¥{{ m.content.price }}</div>
                  <span class="mall-sub">点击查看商品</span>
                </div>
              </div>
              <!-- 订单卡片 -->
              <div v-else-if="m.contentType === 'ORDER'" class="card-bubble order" @click="openProduct(m.content.productId)">
                <img v-if="orderCardCover(m)" :src="orderCardCover(m)" alt="" />
                <div v-else class="order-cover-placeholder"><el-icon><Picture /></el-icon></div>
                <div class="cb-info">
                  <div class="cb-title">{{ orderCardTitle(m) }}</div>
                  <div class="cb-status">{{ cardText[m.cardType || ''] || m.content.statusText || '订单提醒' }}</div>
                  <div class="mall-sub">查看商品</div>
                </div>
              </div>
            </div>
            <div class="msg-time">{{ (m.createTime || '').slice(11, 16) }}</div>
          </div>
        </div>

        <div class="input-bar">
          <el-button circle plain @click="sendImage"><el-icon><Picture /></el-icon></el-button>
          <el-input
            v-model="text"
            type="textarea"
            :rows="1"
            maxlength="500"
            resize="none"
            placeholder="输入消息，Enter 发送"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" @click="send">发送</el-button>
        </div>
      </template>

      <div v-else class="panel-empty">
        <el-empty description="选择左侧会话开始聊天" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat {
  padding: 20px 28px;
  display: flex;
  gap: 18px;
  height: calc(100vh - 116px);
}
.conv-list {
  width: 300px;
  flex: 0 0 300px;
  background: rgba(255, 253, 249, 0.92);
  border: 1px solid var(--mall-border);
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--mall-shadow-md);
  backdrop-filter: blur(16px);
}
.conv-search {
  padding: 14px 14px 10px;
}
.conv {
  display: flex;
  gap: 10px;
  margin: 3px 10px;
  padding: 11px 12px;
  border-radius: 14px;
  cursor: pointer;
  align-items: center;
  transition: background 0.18s ease, transform 0.18s ease;
}
.conv:hover {
  background: var(--mall-surface-muted);
  transform: translateX(2px);
}
.conv.active {
  background: linear-gradient(135deg, #fff1e9 0%, #fff8f3 100%);
  box-shadow: inset 3px 0 0 var(--mall-primary);
}
.conv.sys {
  margin-bottom: 7px;
  border-bottom: 1px solid var(--mall-border);
  border-radius: 14px 14px 0 0;
  color: var(--mall-text-2);
  font-weight: 600;
}
.avatar-wrap {
  position: relative;
}
.online-dot {
  position: absolute;
  right: 0;
  bottom: 2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #52c41a;
  border: 2px solid #fff;
}
.conv-main {
  flex: 1;
  min-width: 0;
}
.conv-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.conv-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-time {
  font-size: 11px;
  color: var(--mall-text-3);
  white-space: nowrap;
}
.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2px;
}
.conv-last {
  font-size: 12px;
  color: var(--mall-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.panel {
  flex: 1;
  min-width: 0;
  background: rgba(255, 253, 249, 0.94);
  border: 1px solid var(--mall-border);
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--mall-shadow-md);
  backdrop-filter: blur(16px);
}
.panel-head {
  height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid var(--mall-border);
  font-weight: 600;
  font-size: 15px;
}
.online-text {
  font-size: 12px;
  color: #52c41a;
  font-weight: 400;
}
.ws-status {
  margin-left: auto;
  font-size: 12px;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background:
    radial-gradient(circle at 80% 5%, rgba(239, 179, 95, 0.08), transparent 20rem),
    linear-gradient(180deg, #fbf8f3 0%, #f8f5ef 100%);
}
.load-more {
  text-align: center;
  padding-bottom: 8px;
}
.msg {
  margin-bottom: 14px;
}
.msg.mine {
  text-align: right;
}
.bubble {
  display: inline-block;
  max-width: 60%;
  text-align: left;
  background: #fff;
  border-radius: 16px;
  padding: 9px 13px;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  box-shadow: 0 6px 18px rgba(72, 55, 37, 0.055);
}
.msg.mine .bubble {
  background: linear-gradient(135deg, #f6855c 0%, var(--mall-primary) 100%);
  color: #fff;
}
.img-bubble {
  max-width: 200px;
  border-radius: 6px;
  display: block;
}
.card-bubble {
  display: flex;
  gap: 10px;
  width: 280px;
  cursor: pointer;
  background: rgba(255, 253, 249, 0.98);
  color: var(--mall-text);
  border: 1px solid var(--mall-border);
  border-radius: 16px;
  padding: 10px;
  box-shadow: 0 12px 28px rgba(72, 55, 37, 0.08);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.card-bubble:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px rgba(72, 55, 37, 0.12);
}
.card-bubble img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
}
.card-bubble.order {
  width: 300px;
  align-items: center;
}
.order-cover-placeholder {
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  border-radius: 6px;
  background: var(--mall-bg);
  color: var(--mall-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.cb-info {
  min-width: 0;
  flex: 1;
}
.cb-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--mall-text);
}
.cb-status {
  font-size: 13px;
  color: var(--mall-text-2);
  margin-bottom: 2px;
}
.msg-time {
  font-size: 11px;
  color: var(--mall-text-3);
  margin-top: 2px;
}
.input-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--mall-border);
  align-items: flex-end;
  background: rgba(255, 253, 249, 0.95);
}
.panel-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.notice-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.notice {
  border: 1px solid var(--mall-border);
  border-radius: 15px;
  padding: 13px 15px;
  margin-bottom: 12px;
  background: rgba(255, 253, 249, 0.82);
  transition: border-color 0.18s ease, transform 0.18s ease;
}
.notice:hover {
  border-color: rgba(242, 107, 63, 0.22);
  transform: translateY(-1px);
}
.notice.unread {
  border-color: var(--mall-primary);
  background: var(--mall-primary-soft);
}
.notice-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 14px;
}
.notice-content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--mall-text-2);
}
.notice-op {
  margin-top: 4px;
}
</style>
