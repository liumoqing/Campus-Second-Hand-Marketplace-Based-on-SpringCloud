<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProductApi } from '@/api'

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const stats = ref<Record<string, number>>({})

const query = reactive({ keyword: '', status: '', categoryId: '' })

const offVisible = ref(false)
const offTarget = ref<any>(null)
const offReason = ref('')

const statusText: Record<string, string> = {
  DRAFT: '草稿',
  ON_SALE: '在售',
  LOCKED: '交易中',
  SOLD: '已售出',
  OFF_SHELF: '已下架'
}

async function load() {
  loading.value = true
  try {
    const page = await adminProductApi.page({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      categoryId: query.categoryId || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    rows.value = page.records
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function openOff(row: any) {
  offTarget.value = row
  offReason.value = ''
  offVisible.value = true
}

async function submitOff() {
  if (!offTarget.value) return
  await adminProductApi.offShelf(offTarget.value.id, offReason.value || '违反平台规则')
  ElMessage.success('已强制下架，卖家会收到通知')
  offVisible.value = false
  load()
  stats.value = await adminProductApi.statistics()
}

onMounted(async () => {
  await load()
  stats.value = await adminProductApi.statistics()
})
</script>

<template>
  <div class="admin-products">
    <h2 class="page-title">商品管理</h2>

    <div class="stats">
      <div class="stat"><b>{{ stats.total ?? 0 }}</b><span>全部</span></div>
      <div class="stat"><b>{{ stats.onSale ?? 0 }}</b><span>在售</span></div>
      <div class="stat"><b>{{ stats.locked ?? 0 }}</b><span>交易中</span></div>
      <div class="stat"><b>{{ stats.sold ?? 0 }}</b><span>已售出</span></div>
      <div class="stat"><b>{{ stats.offShelf ?? 0 }}</b><span>已下架</span></div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="标题关键词" clearable style="width: 220px" @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option v-for="(v, k) in statusText" :key="k" :value="k" :label="v" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="商品" min-width="260">
        <template #default="{ row }">
          <div class="p-cell">
            <img v-if="row.coverUrl" :src="row.coverUrl" alt="" />
            <div>
              <div class="p-title">{{ row.title }}</div>
              <div class="mall-sub">{{ row.categoryName }} · {{ row.city }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="sellerNickname" label="卖家" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ statusText[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览" width="80" />
      <el-table-column prop="favoriteCount" label="收藏" width="80" />
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ON_SALE' || row.status === 'LOCKED'"
            link
            size="small"
            type="danger"
            @click="openOff(row)"
          >
            强制下架
          </el-button>
          <span v-else class="mall-sub">{{ row.offShelfReason || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="offVisible" title="强制下架商品" width="440px">
      <p class="mall-sub">下架后商品不再展示，卖家会收到站内通知；已成交订单不受影响。</p>
      <el-input v-model="offReason" type="textarea" :rows="2" maxlength="200" placeholder="下架原因（会展示给卖家）" />
      <template #footer>
        <el-button @click="offVisible = false">取消</el-button>
        <el-button type="danger" @click="submitOff">确认下架</el-button>
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
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  flex: 1;
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
  margin-bottom: 14px;
}
.p-cell {
  display: flex;
  gap: 10px;
  align-items: center;
}
.p-cell img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 6px;
}
.p-title {
  font-size: 13px;
  margin-bottom: 2px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
