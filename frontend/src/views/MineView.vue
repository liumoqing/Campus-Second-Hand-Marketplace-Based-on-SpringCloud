<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, orderApi, productApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import ProductCardItem from '@/components/ProductCardItem.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import { regions } from '@/data/regions'
import type { AddressItem, ProductCard } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

type TabKey = 'published' | 'drafts' | 'sold' | 'bought' | 'favorite' | 'address' | 'reviews'
const tab = ref<TabKey>(((route.params.tab as TabKey) || 'published') as TabKey)

const stats = ref<Record<string, number>>({})
const products = ref<ProductCard[]>([])
const favorites = ref<ProductCard[]>([])
const addresses = ref<AddressItem[]>([])
const reviews = ref<any[]>([])
const loading = ref(false)

const signature = ref('')
const editingSign = ref(false)
const nickname = ref('')
const profileRegion = ref<string[]>([])
const avatarInput = ref<HTMLInputElement>()
const avatarUploading = ref(false)

const addrDialog = ref(false)
const editingAddr = ref<AddressItem | null>(null)

const tabs: { key: TabKey; label: string }[] = [
  { key: 'published', label: '我发布的' },
  { key: 'drafts', label: '草稿箱' },
  { key: 'sold', label: '我卖出的' },
  { key: 'bought', label: '我买到的' },
  { key: 'favorite', label: '我的收藏' },
  { key: 'address', label: '收货地址' },
  { key: 'reviews', label: '我的评价' }
]

const profile = computed(() => user.profile)
const profileLocation = computed(() => {
  const p = profile.value
  return [p?.province, p?.city, p?.district].filter(Boolean).join(' / ') || '未填写地区'
})

async function loadStats() {
  stats.value = await userApi.statistics()
}

async function loadTab() {
  loading.value = true
  try {
    products.value = []
    favorites.value = []
    addresses.value = []
    reviews.value = []
    if (tab.value === 'published' || tab.value === 'drafts') {
      const page = await productApi.myPage({
        status: tab.value === 'drafts' ? 'DRAFT' : 'ON_SALE',
        pageNum: 1,
        pageSize: 40
      })
      products.value = page.records
    } else if (tab.value === 'favorite') {
      const page = await productApi.favoritePage({ pageNum: 1, pageSize: 40 })
      favorites.value = page.records
    } else if (tab.value === 'address') {
      addresses.value = await userApi.addresses()
    } else if (tab.value === 'reviews') {
      const page = await orderApi.reviewMine()
      reviews.value = page.records
    } else if (tab.value === 'sold' || tab.value === 'bought') {
      router.push({ path: '/orders', query: { tab: 'ALL' } })
    }
  } finally {
    loading.value = false
  }
}

function switchTab(k: TabKey) {
  tab.value = k
  router.replace(`/mine/${k}`)
}

function pickAvatar() {
  if (!avatarUploading.value) avatarInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.error('只支持 jpg/png/webp/gif 图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像不能超过 2MB')
    return
  }
  avatarUploading.value = true
  try {
    const r = await authApi.uploadAvatar(file)
    await user.updateProfile({ avatar: r.url })
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

function syncProfileRegion() {
  profileRegion.value = [profile.value?.province || '', profile.value?.city || '', profile.value?.district || ''].filter(Boolean)
}

function beginEditProfile() {
  signature.value = profile.value?.signature || ''
  nickname.value = profile.value?.nickname || ''
  syncProfileRegion()
  editingSign.value = true
}

async function saveSignature() {
  if (profileRegion.value.length !== 3) {
    ElMessage.warning('请选择完整的省 / 市 / 区')
    return
  }
  const [province, city, district] = profileRegion.value
  await user.updateProfile({
    signature: signature.value,
    nickname: nickname.value || profile.value?.nickname,
    province,
    city,
    district
  })
  ElMessage.success('资料已更新')
  editingSign.value = false
}

async function onAddressSaved() {
  await loadTab()
  await user.fetchMe()
  syncProfileRegion()
}

async function removeProduct(p: ProductCard) {
  await productApi.remove(p.id)
  ElMessage.success('已删除')
  loadTab()
  loadStats()
}

async function publishDraft(p: ProductCard) {
  await productApi.publishDraft(p.id)
  ElMessage.success('已发布')
  loadTab()
}

function editAddr(a: AddressItem) {
  editingAddr.value = a
  addrDialog.value = true
}

async function delAddr(a: AddressItem) {
  await userApi.deleteAddress(a.id)
  ElMessage.success('已删除')
  await loadTab()
  await user.fetchMe()
  syncProfileRegion()
}

async function setDefault(a: AddressItem) {
  await userApi.setDefaultAddress(a.id)
  ElMessage.success('已设为默认，资料地区已同步')
  await loadTab()
  await user.fetchMe()
  syncProfileRegion()
}

watch(
  () => route.params.tab,
  (v) => {
    tab.value = ((v as TabKey) || 'published') as TabKey
    loadTab()
  }
)

onMounted(async () => {
  await user.fetchMe()
  signature.value = profile.value?.signature || ''
  nickname.value = profile.value?.nickname || ''
  syncProfileRegion()
  await Promise.all([loadStats(), loadTab()])
})
</script>

<template>
  <div class="mine mall-page">
    <div class="profile mall-card">
      <div class="avatar-edit" :class="{ uploading: avatarUploading }" @click="pickAvatar">
        <el-avatar :size="80" :src="profile?.avatar || undefined">
          {{ (profile?.nickname || '?').slice(0, 1) }}
        </el-avatar>
        <div class="avatar-mask">
          <el-icon><Camera /></el-icon>
          <span>{{ avatarUploading ? '上传中…' : '更换头像' }}</span>
        </div>
        <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="onAvatarChange" />
      </div>
      <div class="p-info">
        <div class="p-name">
          <span>{{ profile?.nickname }}</span>
          <el-tag v-if="profile?.role === 'SUPER_ADMIN'" size="small" type="danger">超级管理员</el-tag>
          <el-tag v-else-if="profile?.role === 'ADMIN'" size="small" type="warning">管理员</el-tag>
        </div>
        <div class="p-city mall-sub" title="点击编辑地区" @click="beginEditProfile">{{ profileLocation }} · 信用分 {{ profile?.creditScore ?? '-' }}</div>

        <div v-if="!editingSign" class="sign-box" @click="editingSign = true">
          {{ profile?.signature || '点击填写个性签名…' }}
        </div>
        <div v-else class="sign-edit">
          <el-input v-model="signature" maxlength="200" show-word-limit placeholder="一句话介绍自己" />
          <el-input v-model="nickname" maxlength="20" placeholder="昵称" class="nick-input" />
          <el-cascader v-model="profileRegion" :options="regions" :props="{ value: 'value', label: 'label' }" placeholder="省 / 市 / 区" class="region-input" />
          <el-button size="small" type="primary" @click="saveSignature">保存</el-button>
          <el-button size="small" @click="editingSign = false">取消</el-button>
        </div>

        <div class="p-stats">
          <span><b>{{ stats.onSaleCount ?? 0 }}</b>在售</span>
          <span><b>{{ stats.soldCount ?? 0 }}</b>卖出</span>
          <span><b>{{ stats.favoriteCount ?? 0 }}</b>收藏</span>
          <span><b>{{ stats.draftCount ?? 0 }}</b>草稿</span>
        </div>
      </div>
      <el-button class="p-edit" plain @click="beginEditProfile">编辑资料</el-button>
    </div>

    <div class="body">
      <aside class="side">
        <div class="side-item" :class="{ active: tab === 'published' }" @click="switchTab('published')">我的</div>
        <div class="side-item" @click="router.push('/orders')">我的交易</div>
        <div class="side-sub">
          <div :class="{ active: tab === 'published' }" @click="switchTab('published')">我发布的</div>
          <div @click="router.push('/orders?tab=ALL')">我卖出的</div>
          <div @click="router.push('/orders?tab=ALL')">我买到的</div>
        </div>
        <div class="side-item" :class="{ active: tab === 'favorite' }" @click="switchTab('favorite')">我的收藏</div>
        <div class="side-item" :class="{ active: tab === 'address' }" @click="switchTab('address')">收货地址</div>
        <div class="side-item" :class="{ active: tab === 'reviews' }" @click="switchTab('reviews')">我的评价</div>
        <div class="side-item" :class="{ active: tab === 'drafts' }" @click="switchTab('drafts')">草稿箱</div>
      </aside>

      <section class="content" v-loading="loading">
        <!-- 商品列表（我发布的 / 草稿箱 / 收藏） -->
        <template v-if="tab === 'published' || tab === 'drafts' || tab === 'favorite'">
          <div class="grid">
            <div v-for="p in tab === 'favorite' ? favorites : products" :key="p.id" class="grid-item">
              <ProductCardItem :item="p" @select="router.push(tab === 'favorite' ? `/product/${p.id}` : `/publish/${p.id}`)" />
              <div v-if="tab !== 'favorite'" class="item-ops">
                <el-button v-if="tab === 'drafts'" size="small" type="primary" @click="publishDraft(p)">发布</el-button>
                <el-button size="small" @click="router.push(`/publish/${p.id}`)">编辑</el-button>
                <el-button size="small" @click="removeProduct(p)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty
            v-if="!(tab === 'favorite' ? favorites : products).length"
            :description="tab === 'drafts' ? '草稿箱是空的' : tab === 'favorite' ? '还没有收藏任何商品' : '还没有发布商品'"
          />
        </template>

        <!-- 地址 -->
        <template v-else-if="tab === 'address'">
          <div class="addr-head">
            <h3>收货地址</h3>
            <el-button type="primary" size="small" @click="editingAddr = null; addrDialog = true">新增地址</el-button>
          </div>
          <div v-for="a in addresses" :key="a.id" class="addr">
            <div>
              <b>{{ a.receiver }}</b> {{ a.phone }}
              <el-tag v-if="a.isDefault" size="small" type="warning">默认</el-tag>
            </div>
            <div class="mall-sub">{{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}</div>
            <div class="addr-ops">
              <el-button link size="small" @click="setDefault(a)">设为默认</el-button>
              <el-button link size="small" @click="editAddr(a)">编辑</el-button>
              <el-button link size="small" @click="delAddr(a)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!addresses.length" description="还没有收货地址" />
        </template>

        <!-- 评价 -->
        <template v-else-if="tab === 'reviews'">
          <div v-for="(r, i) in reviews" :key="i" class="review mall-card">
            <div class="r-head">
              <b>{{ r.direction === 'BUYER_TO_SELLER' ? '我给卖家的评价' : '我给买家的评价' }}</b>
              <span class="mall-sub">{{ r.createTime }}</span>
            </div>
            <div class="r-body">评分 {{ r.rating }} 星 · {{ r.content || '（未填写文字）' }}</div>
          </div>
          <el-empty v-if="!reviews.length" description="还没有发表过评价" />
        </template>
      </section>
    </div>

    <AddressFormDialog v-model="addrDialog" :address="editingAddr" @saved="onAddressSaved" />
  </div>
</template>

<style scoped>
.mine {
  padding: 24px;
}
.profile {
  display: flex;
  gap: 20px;
  padding: 20px 24px;
  align-items: flex-start;
  position: relative;
}
.avatar-edit {
  width: 80px;
  height: 80px;
  flex: 0 0 80px;
  border-radius: 50%;
  position: relative;
  overflow: hidden;
  cursor: pointer;
}
.avatar-edit input { display: none; }
.avatar-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, .52);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 11px;
  opacity: 0;
  transition: opacity .18s;
}
.avatar-edit:hover .avatar-mask,
.avatar-edit.uploading .avatar-mask { opacity: 1; }
.p-info {
  flex: 1;
  min-width: 0;
}
.p-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}
.p-city {
  margin: 4px 0 10px;
}
.sign-box {
  border: 1px dashed var(--mall-border);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--mall-text-2);
  cursor: pointer;
  min-height: 40px;
  background: #fafbfc;
}
.sign-edit {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.nick-input {
  width: 160px;
}
.region-input { width: 260px; }
.p-city { cursor: pointer; }
.p-stats {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--mall-text-2);
}
.p-stats b {
  color: var(--mall-text);
  font-size: 16px;
  margin-right: 4px;
}
.p-edit {
  position: absolute;
  right: 24px;
  top: 20px;
}
.body {
  display: flex;
  gap: 20px;
  margin-top: 16px;
  align-items: flex-start;
}
.side {
  width: 180px;
  flex: 0 0 180px;
  background: #fff;
  border-radius: 8px;
  padding: 10px 8px;
  position: sticky;
  top: 88px;
}
.side-item {
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  color: var(--mall-text-2);
}
.side-item:hover,
.side-item.active {
  background: var(--mall-primary-light);
  color: var(--mall-primary);
  font-weight: 600;
}
.side-sub {
  padding-left: 12px;
  font-size: 13px;
  color: var(--mall-text-3);
}
.side-sub div {
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 6px;
}
.side-sub div:hover,
.side-sub div.active {
  color: var(--mall-primary);
  background: #fff7f3;
}
.content {
  flex: 1;
  min-width: 0;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.grid-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.item-ops {
  display: flex;
  gap: 6px;
}
.addr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.addr-head h3 {
  margin: 0;
  font-size: 15px;
}
.addr {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--mall-border);
}
.addr-ops {
  margin-top: 6px;
  display: flex;
  gap: 10px;
}
.review {
  padding: 14px 16px;
  margin-bottom: 12px;
}
.r-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 14px;
}
.r-body {
  margin-top: 8px;
  font-size: 13px;
  color: var(--mall-text-2);
}
</style>
