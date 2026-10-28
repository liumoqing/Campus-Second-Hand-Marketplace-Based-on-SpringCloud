<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminOrderApi } from '@/api'

const tab = ref<'orders' | 'refunds'>('orders')
const rows = ref<any[]>([])
const refunds = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const stats = ref<Record<string, number>>({})

const query = reactive({ status: '', keyword: '' })

const statusText: Record<string, string> = {
  UNPAID: '待付款',
  UNSHIPPED: '待发货',
  UNRECEIVED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

const refuseVisible = ref(false)
const handleTarget = ref<any>(null)
const handleReason = ref('')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'orders') {
      const page = await adminOrderApi.page({
        status: query.status || undefined,
        keyword: query.keyword || undefined,
        pageNum: pageNum.value,
        pageSize: pageSize.value
      })
      rows.value = page.records
      total.value = Number(page.total)
    } else {
      const page = await adminOrderApi.refundPage({ pageNum: pageNum.value, pageSize: pageSize.value })
      refunds.value = page.records
      total.value = Number(page.total)
    }
  } finally {
    loading.value = false
  }
}

function switchTab(t: 'orders' | 'refunds') {
  tab.value = t
  pageNum.value = 1
  load()
}

async function agree(r: any) {
  await ElMessageBox.confirm(
    `同意退款 ¥${r.amount}？同意后将调用支付服务退款，未发货订单的商品会重新上架。`,
    '同意退款',
    { type: 'warning' }
  )
  await adminOrderApi.handleRefund(r.id, { agree: true, remark: '管理员同意退款' })
  ElMessage.success('已同意退款')
  load()
}

function openRefuse(r: any) {
  handleTarget.value = r
  handleReason.value = ''
  refuseVisible.value = true
}

async function submitRefuse() {
  if (!handleTarget.value) return
  await adminOrderApi.handleRefund(handleTarget.value.id, {
    agree: false,
    remark: handleReason.value || '不符合售后条件'
  })
  ElMessage.success('已拒绝，订单会回到申请前的状态')
  refuseVisible.value = false
  load()
}

onMounted(async () => {
  await load()
  stats.value = await adminOrderApi.statistics()
})
</script>

<template>
  <div class="admin-orders">
    <h2 class="page-title">订单与退款</h2>

    <div class="stats">
      <div class="stat"><b>{{ stats.total ?? 0 }}</b><span>订单总数</span></div>
      <div class="stat"><b>{{ stats.unpaid ?? 0 }}</b><span>待付款</span></div>
      <div class="stat"><b>{{ stats.unshipped ?? 0 }}</b><span>待发货</span></div>
      <div class="stat"><b>{{ stats.unreceived ?? 0 }}</b><span>待收货</span></div>
      <div class="stat"><b>{{ stats.completed ?? 0 }}</b><span>已完成</span></div>
      <div class="stat"><b>{{ stats.refunding ?? 0 }}</b><span>退款中</span></div>
    </div>

    <el-radio-group v-model="tab" size="small" @change="(v: any) => switchTab(v)">
      <el-radio-button value="orders">订单</el-radio-button>
      <el-radio-button value="refunds">退款 / 售后</el-radio-button>
    </el-radio-group>

    <template v-if="tab === 'orders'">
      <div class="filters">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusText" :key="k" :value="k" :label="v" />
        </el-select>
        <el-button type="primary" @click="pageNum = 1; load()">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="210" />
        <el-table-column prop="productTitle" label="商品" min-width="200" show-overflow-tooltip />
        <el-table-column prop="buyerNickname" label="买家" width="110" />
        <el-table-column prop="sellerNickname" label="卖家" width="110" />
        <el-table-column prop="payAmount" label="实付" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="170" />
      </el-table>
    </template>

    <template v-else>
      <el-table :data="refunds" v-loading="loading" stripe class="refund-table">
        <el-table-column prop="refundNo" label="退款单号" width="200" />
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="beforeStatus" label="申请前状态" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'REFUNDED' ? 'success' : row.status === 'REJECTED' ? 'info' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'APPLYING'">
              <el-button link size="small" type="primary" @click="agree(row)">同意</el-button>
              <el-button link size="small" type="danger" @click="openRefuse(row)">拒绝</el-button>
            </template>
            <span v-else class="mall-sub">{{ row.handleRemark || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="refuseVisible" title="拒绝退款" width="420px">
      <el-input v-model="handleReason" type="textarea" :rows="3" maxlength="200" placeholder="拒绝原因（会展示给买家）" />
      <template #footer>
        <el-button @click="refuseVisible = false">取消</el-button>
        <el-button type="danger" @click="submitRefuse">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 18px;
  margin: 0 0 14px;
}
.stats {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat {
  flex: 1;
  min-width: 110px;
  background: #fafbfc;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
}
.stat b {
  font-size: 20px;
}
.stat span {
  font-size: 12px;
  color: var(--mall-text-3);
}
.filters {
  display: flex;
  gap: 10px;
  margin: 14px 0;
}
.refund-table {
  margin-top: 14px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
