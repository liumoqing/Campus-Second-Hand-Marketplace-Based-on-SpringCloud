<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, paymentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import StarRating from '@/components/StarRating.vue'
import type { OrderItem } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const tabs = [
  { key: 'UNPAID', label: '待付款' },
  { key: 'UNSHIPPED', label: '待发货' },
  { key: 'UNRECEIVED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'REFUND', label: '退款/售后' },
  { key: 'ALL', label: '全部' }
]

const active = ref((route.query.tab as string) || 'UNPAID')
const role = ref<'bought' | 'sold'>(route.query.role === 'sold' ? 'sold' : 'bought')
const orders = ref<OrderItem[]>([])
const refunds = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const finished = ref(false)
const pageSize = 10

/** 待付款倒计时（服务端 expireTime 为准） */
const now = ref(Date.now())
const ticker = window.setInterval(() => (now.value = Date.now()), 1000)

function remain(o: OrderItem) {
  if (!o.expireTime) return ''
  const left = new Date(o.expireTime.replace(' ', 'T')).getTime() - now.value
  if (left <= 0) return '已超时'
  const m = Math.floor(left / 60000)
  const s = Math.floor((left % 60000) / 1000)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const statusText: Record<string, string> = {
  UNPAID: '待付款',
  UNSHIPPED: '待发货',
  UNRECEIVED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

const isSeller = (o: OrderItem) => o.sellerId === user.profile?.id

async function load(reset = false) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    if (active.value === 'REFUND') {
      const page = await orderApi.refundPage({ role: role.value, pageNum: pageNum.value, pageSize })
      refunds.value = reset ? page.records : [...refunds.value, ...page.records]
      finished.value = refunds.value.length >= Number(page.total)
      return
    }
    const page = await orderApi.page({
      role: role.value,
      status: active.value === 'ALL' ? undefined : active.value,
      pageNum: pageNum.value,
      pageSize
    })
    orders.value = reset ? page.records : [...orders.value, ...page.records]
    finished.value = orders.value.length >= Number(page.total)
  } finally {
    loading.value = false
  }
}

function switchTab(k: string) {
  active.value = k
  router.replace({ query: { ...route.query, tab: k } })
}

async function pay(o: OrderItem) {
  await ElMessageBox.confirm(`确认支付 ¥${o.payAmount}？（模拟支付，不会真的扣款）`, '确认支付', {
    type: 'warning'
  })
  await paymentApi.pay(o.orderNo)
  ElMessage.success('支付成功')
  load(true)
}

async function cancel(o: OrderItem) {
  await ElMessageBox.confirm('确定取消这笔订单？商品会重新上架。', '取消订单', { type: 'warning' })
  await orderApi.cancel(o.orderNo, '买家取消')
  ElMessage.success('已取消')
  load(true)
}

async function ship(o: OrderItem) {
  const { value } = await ElMessageBox.prompt('填写物流单号（选填）', '确认发货', {
    inputPlaceholder: '例如 SF1234567890',
    confirmButtonText: '确认发货'
  })
  await orderApi.ship(o.orderNo, value || undefined)
  ElMessage.success('已发货')
  load(true)
}

async function receive(o: OrderItem) {
  await ElMessageBox.confirm('确认已收到商品？确认后钱款将结算给卖家。', '确认收货', { type: 'warning' })
  await orderApi.receive(o.orderNo)
  ElMessage.success('已确认收货')
  load(true)
}

/* ---- 评价 ---- */
const reviewVisible = ref(false)
const reviewTarget = ref<OrderItem | null>(null)
const reviewForm = reactive({ rating: 5, content: '', anonymous: false })
const reviewSubmitting = ref(false)

function openReview(o: OrderItem) {
  reviewTarget.value = o
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewForm.anonymous = false
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewTarget.value) return
  reviewSubmitting.value = true
  try {
    await orderApi.review(reviewTarget.value.orderNo, {
      rating: reviewForm.rating,
      content: reviewForm.content || undefined,
      anonymous: reviewForm.anonymous
    })
    ElMessage.success('评价成功')
    reviewVisible.value = false
    load(true)
  } finally {
    reviewSubmitting.value = false
  }
}

/* ---- 退款 ---- */
const refundVisible = ref(false)
const refundTarget = ref<OrderItem | null>(null)
const refundForm = reactive({ amount: 0, reason: '' })
const refundStatusText: Record<string, string> = {
  APPLYING: '待处理', AGREED: '已同意', REJECTED: '已拒绝', REFUNDED: '已退款', CLOSED: '已关闭'
}

function openRefund(o: OrderItem) {
  refundTarget.value = o
  refundForm.amount = Number(o.payAmount)
  refundForm.reason = ''
  refundVisible.value = true
}

async function handleRefund(r: any, agree: boolean) {
  const action = agree ? '同意退款' : '拒绝退款'
  const { value } = await ElMessageBox.prompt('可填写处理备注', `确认${action}`, {
    confirmButtonText: `确认${action}`,
    cancelButtonText: '取消',
    inputPlaceholder: '处理备注（选填）'
  })
  await orderApi.handleRefund(String(r.refundId || r.id), { agree, remark: value || undefined })
  ElMessage.success('退款已处理')
  await load(true)
}

async function submitRefund() {
  if (!refundTarget.value) return
  if (!refundForm.reason.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  await orderApi.refundApply({
    orderNo: refundTarget.value.orderNo,
    amount: refundForm.amount,
    reason: refundForm.reason
  })
  ElMessage.success('退款申请已提交')
  refundVisible.value = false
  load(true)
}

watch(active, () => load(true))
watch(role, () => {
  const queryRole = role.value === 'sold' ? 'sold' : undefined
  if ((route.query.role as string | undefined) !== queryRole) {
    router.replace({ query: { ...route.query, role: queryRole } })
  }
  load(true)
})
watch(
  () => [route.query.tab, route.query.role],
  ([tab, queryRole]) => {
    const nextTab = typeof tab === 'string' && tabs.some((t) => t.key === tab) ? tab : active.value
    if (nextTab !== active.value) active.value = nextTab
    const nextRole = queryRole === 'sold' ? 'sold' : 'bought'
    if (nextRole !== role.value) role.value = nextRole
  }
)
onMounted(() => load(true))
</script>

<template>
  <div class="orders mall-page">
    <div class="head">
      <h2 class="page-title">我的订单</h2>
      <el-radio-group v-model="role" size="small">
        <el-radio-button value="bought">我买到的</el-radio-button>
        <el-radio-button value="sold">我卖出的</el-radio-button>
      </el-radio-group>
    </div>

    <el-tabs :model-value="active" @update:model-value="switchTab">
      <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key" :label="t.label" />
    </el-tabs>

    <div v-loading="loading">
      <template v-if="active === 'REFUND'">
        <div v-for="r in refunds" :key="r.refundId || r.id || r.refundNo" class="order mall-card">
          <div class="o-head">
            <span class="o-no">退款单 {{ r.refundNo }}</span>
            <el-tag size="small">{{ refundStatusText[r.status] || r.status }}</el-tag>
          </div>
          <div class="o-line">订单 {{ r.orderNo }} · 金额 ¥{{ r.amount }}</div>
          <div class="o-line">原因：{{ r.reason }}</div>
          <div class="o-line mall-sub">申请时间 {{ r.applyTime || r.createTime }}</div>
          <div v-if="role === 'sold' && r.status === 'APPLYING'" class="o-actions refund-actions">
            <el-button type="primary" size="small" @click="handleRefund(r, true)">同意退款</el-button>
            <el-button type="danger" size="small" @click="handleRefund(r, false)">拒绝退款</el-button>
          </div>
        </div>
        <el-empty v-if="!refunds.length" description="没有退款/售后记录" />
      </template>

      <template v-else>
        <div v-for="o in orders" :key="o.orderNo" class="order mall-card">
          <div class="o-head">
            <span class="o-no">{{ o.orderNo }}</span>
            <span class="o-status">{{ statusText[o.status] || o.status }}</span>
          </div>

          <div class="o-body">
            <img v-if="o.productCover" :src="o.productCover" alt="" class="cover" />
            <div class="o-info">
              <div class="o-title" @click="router.push(`/product/${o.productId}`)">{{ o.productTitle }}</div>
              <div class="mall-sub">
                对方：{{ o.counterpartNickname || '-' }}
                <template v-if="o.logisticsNo"> · 物流 {{ o.logisticsNo }}</template>
              </div>
              <div v-if="role === 'sold'" class="buyer-info">
                <div><span>买家</span>{{ o.buyerName || o.counterpartNickname || '-' }}</div>
                <div><span>电话</span>{{ o.buyerPhone || '-' }}</div>
                <div><span>地址</span>{{ o.buyerAddress || '-' }}</div>
              </div>
              <div v-if="o.status === 'UNPAID' && o.expireTime" class="countdown">
                剩余支付时间 {{ remain(o) }}
              </div>
            </div>
            <div class="o-amount">
              <div class="mall-price">¥{{ o.payAmount }}</div>
              <div class="mall-sub">含运费 ¥{{ o.freight }}</div>
            </div>
          </div>

          <div class="o-actions">
            <template v-if="o.status === 'UNPAID'">
              <template v-if="!isSeller(o)">
                <el-button size="small" @click="cancel(o)">取消订单</el-button>
                <el-button type="primary" size="small" @click="pay(o)">去支付</el-button>
              </template>
            </template>
            <template v-else-if="o.status === 'UNSHIPPED'">
              <el-button v-if="isSeller(o)" type="primary" size="small" @click="ship(o)">发货</el-button>
              <el-button v-else size="small" @click="openRefund(o)">申请退款</el-button>
            </template>
            <template v-else-if="o.status === 'UNRECEIVED'">
              <span v-if="o.logisticsNo" class="mall-sub">物流单号 {{ o.logisticsNo }}</span>
              <el-button v-if="!isSeller(o)" type="primary" size="small" @click="receive(o)">确认收货</el-button>
              <el-button v-if="!isSeller(o)" size="small" @click="openRefund(o)">申请售后</el-button>
            </template>
            <template v-else-if="o.status === 'COMPLETED'">
              <el-button
                v-if="!(isSeller(o) ? o.sellerReviewed : o.buyerReviewed)"
                size="small"
                type="primary"
                @click="openReview(o)"
              >
                去评价
              </el-button>
              <el-button v-if="!isSeller(o)" size="small" @click="openRefund(o)">申请售后</el-button>
            </template>
            <el-button size="small" link @click="router.push(`/product/${o.productId}`)">查看商品</el-button>
          </div>
        </div>
        <el-empty v-if="!orders.length && !loading" description="暂无订单" />
        <div class="more">
          <el-button v-if="!finished && orders.length" link @click="pageNum++; load()">加载更多</el-button>
        </div>
      </template>
    </div>

    <el-dialog v-model="reviewVisible" title="评价这笔交易" width="440px">
      <div class="rv">
        <div class="rv-row">
          <span>评分</span>
          <StarRating v-model="reviewForm.rating" :readonly="false" :size="26" />
        </div>
        <el-input
          v-model="reviewForm.content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说说这次交易体验：描述是否属实、发货速度、沟通态度…"
        />
        <el-checkbox v-model="reviewForm.anonymous">匿名评价</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="refundVisible" title="申请退款 / 售后" width="440px">
      <el-form label-width="80px">
        <el-form-item label="退款金额">
          <el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRefund">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.orders {
  padding: 28px;
  max-width: 1100px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.page-title {
  font-size: 28px;
  margin: 0;
}
.order {
  padding: 17px 20px;
  margin-bottom: 16px;
  border-color: rgba(58, 49, 39, 0.08);
  border-radius: 22px;
  box-shadow: 0 10px 30px rgba(72, 55, 37, 0.06);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.order:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 38px rgba(72, 55, 37, 0.1);
}
.o-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mall-border);
  font-size: 13px;
  color: var(--mall-text-2);
}
.o-status {
  color: var(--mall-primary);
  font-weight: 600;
}
.o-body {
  display: flex;
  gap: 14px;
  padding: 15px 0;
  align-items: center;
}
.cover {
  width: 86px;
  height: 86px;
  object-fit: cover;
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(72, 55, 37, 0.1);
}
.o-info {
  flex: 1;
  min-width: 0;
}
.o-title {
  font-size: 16px;
  font-weight: 680;
  margin-bottom: 6px;
  cursor: pointer;
}
.o-title:hover {
  color: var(--mall-primary);
}
.countdown {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mall-price);
}
.o-amount {
  text-align: right;
}
.refund-actions { margin-top: 8px; }
.buyer-info {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--mall-surface-muted);
  font-size: 12px;
  line-height: 1.8;
  color: var(--mall-text-2);
}
.buyer-info span {
  display: inline-block;
  width: 38px;
  color: var(--mall-text-3);
}
.o-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
}
.o-line {
  padding: 6px 0;
  font-size: 13px;
}
.more {
  text-align: center;
  padding: 8px 0 16px;
}
.rv {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rv-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
</style>
