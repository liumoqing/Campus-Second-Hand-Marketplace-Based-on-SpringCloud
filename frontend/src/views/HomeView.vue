<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import { useUserStore } from '@/stores/user'
import WaterfallGrid from '@/components/WaterfallGrid.vue'
import ProductCardItem from '@/components/ProductCardItem.vue'
import type { CategoryNode, ProductCard as PC } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const categories = ref<CategoryNode[]>([])
const expandedCategoryIds = ref<string[]>([])
const items = ref<PC[]>([])
const loading = ref(false)
const guestLimited = ref(false)
const pageNum = ref(1)
const pageSize = 20
const finished = ref(false)

const sorts = [
  { value: 'new', label: '最新发布' },
  { value: 'oldest', label: '最早发布' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'hot', label: '最热' },
  { value: 'nearby', label: '同城优先' }
]

const keyword = computed(() => (route.query.keyword as string) || '')
const categoryId = computed(() => (route.query.categoryId as string) || '')
const sort = computed(() => (route.query.sort as string) || 'new')
const city = computed(() => (route.query.city as string) || '')

const columns = ref(5)

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    pageNum.value = 1
    finished.value = false
  }
  if (finished.value && !reset) return
  loading.value = true
  try {
    const page = await productApi.page({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      sort: sort.value,
      city: city.value || undefined,
      pageNum: pageNum.value,
      pageSize
    })
    guestLimited.value = !!page.guestLimited
    items.value = reset ? page.records : [...items.value, ...page.records]
    // 游客只 20 条：不翻页
    finished.value = guestLimited.value || items.value.length >= Number(page.total)
  } finally {
    loading.value = false
  }
}

function setQuery(patch: Record<string, string | undefined>) {
  router.replace({ query: { ...route.query, ...patch } })
}

function pickCategory(id: string) {
  setQuery({ categoryId: categoryId.value === id ? undefined : id })
}

function isExpanded(id: string) {
  return expandedCategoryIds.value.includes(id)
}

function toggleTopCategory(category: CategoryNode) {
  const hasChildren = !!category.children?.length
  if (hasChildren) {
    if (categoryId.value === category.id) {
      expandedCategoryIds.value = isExpanded(category.id)
        ? expandedCategoryIds.value.filter((id) => id !== category.id)
        : [...expandedCategoryIds.value, category.id]
    } else if (!isExpanded(category.id)) {
      expandedCategoryIds.value = [...expandedCategoryIds.value, category.id]
    }
  }
  pickCategory(category.id)
}

function clearCategory() {
  expandedCategoryIds.value = []
  setQuery({ categoryId: undefined })
}

function revealActiveParent() {
  if (!categoryId.value) return
  const parent = categories.value.find(
    (c) => c.id === categoryId.value || (c.children || []).some((s) => s.id === categoryId.value)
  )
  if (parent && (parent.children || []).length && !isExpanded(parent.id)) {
    expandedCategoryIds.value = [...expandedCategoryIds.value, parent.id]
  }
}

function pickSort(v: string) {
  setQuery({ sort: v === 'new' ? undefined : v })
}

function openProduct(item: PC) {
  if (!user.isLogin) {
    router.push({ path: '/login', query: { redirect: `/product/${item.id}` } })
    return
  }
  router.push(`/product/${item.id}`)
}

function onScroll() {
  if (guestLimited.value || finished.value) return
  const el = document.documentElement
  if (el.scrollTop + window.innerHeight >= el.scrollHeight - 300) {
    pageNum.value += 1
    load()
  }
}

function onResize() {
  const w = window.innerWidth
  columns.value = w >= 1440 ? 5 : w >= 1200 ? 4 : w >= 992 ? 3 : 2
}

onMounted(async () => {
  onResize()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)
  categories.value = await productApi.categories()
  revealActiveParent()
  await load(true)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
})

watch(
  () => [route.query.keyword, route.query.categoryId, route.query.sort, route.query.city],
  () => load(true)
)

watch(categoryId, revealActiveParent)
</script>

<template>
  <div class="home mall-page">
    <aside class="cats">
      <div class="cats-title">全部分类</div>
      <div class="cat" :class="{ active: !categoryId }" @click="clearCategory">全部</div>
      <template v-for="c in categories" :key="c.id">
        <div
          class="cat top"
          :class="{ active: categoryId === c.id, 'has-children': !!c.children?.length, expanded: isExpanded(c.id) }"
          role="button"
          tabindex="0"
          @click="toggleTopCategory(c)"
          @keydown.enter="toggleTopCategory(c)"
        >
          <span>{{ c.name }}</span>
          <span v-if="c.children?.length" class="cat-arrow">{{ isExpanded(c.id) ? '▾' : '▸' }}</span>
        </div>
        <template v-if="isExpanded(c.id)">
          <div
            v-for="s in c.children || []"
            :key="s.id"
            class="cat sub"
            :class="{ active: categoryId === s.id }"
            @click="pickCategory(s.id)"
          >
            {{ s.name }}
          </div>
        </template>
      </template>
    </aside>

    <section class="content">
      <div class="toolbar">
        <div class="result">
          <template v-if="keyword">搜索「{{ keyword }}」</template>
          <template v-else>最新闲置</template>
          <span class="count">共 {{ items.length }} 件</span>
        </div>
        <div class="sort">
          <span
            v-for="s in sorts"
            :key="s.value"
            class="sort-item"
            :class="{ active: sort === s.value }"
            @click="pickSort(s.value)"
          >
            {{ s.label }}
          </span>
        </div>
      </div>

      <el-alert
        v-if="guestLimited"
        type="info"
        :closable="false"
        show-icon
        title="登录后可查看全部商品"
        description="当前为游客浏览，最多展示 20 件商品且不能翻页。"
        class="guest-tip"
      />

      <WaterfallGrid v-if="items.length" :items="items" :columns="columns">
        <template #default="{ item }">
          <ProductCardItem :item="item" @select="openProduct" />
        </template>
      </WaterfallGrid>

      <div v-else-if="!loading" class="mall-empty">还没有商品，去发布第一件闲置吧</div>

      <div class="footer">
        <el-button v-if="guestLimited" type="primary" plain @click="router.push('/login')">
          登录查看全部商品 ›
        </el-button>
        <span v-else-if="loading">加载中…</span>
        <span v-else-if="finished && items.length">没有更多了</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  gap: 24px;
  padding: 24px;
  align-items: flex-start;
}
.cats {
  width: 200px;
  flex: 0 0 200px;
  position: sticky;
  top: 88px;
  background: #fff;
  border-radius: 8px;
  padding: 12px 8px;
  max-height: calc(100vh - 120px);
  overflow: auto;
}
.cats-title {
  font-size: 13px;
  color: var(--mall-text-3);
  padding: 4px 8px 8px;
}
.cat {
  padding: 7px 10px;
  font-size: 14px;
  color: var(--mall-text-2);
  border-radius: 6px;
  cursor: pointer;
}
.cat.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cat-arrow {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--mall-text-3);
}
.cat.top.expanded .cat-arrow {
  color: var(--mall-primary);
}
.cat.sub {
  padding-left: 22px;
  font-size: 13px;
}
.cat:hover {
  background: #f5f6f8;
}
.cat.active {
  color: var(--mall-primary);
  background: var(--mall-primary-light);
  font-weight: 600;
}
.content {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 16px;
}
.result {
  font-size: 16px;
  font-weight: 600;
}
.count {
  font-size: 12px;
  color: var(--mall-text-3);
  font-weight: 400;
  margin-left: 8px;
}
.sort {
  display: flex;
  gap: 4px;
  background: #fff;
  border-radius: 8px;
  padding: 4px;
  position: sticky;
  top: 88px;
}
.sort-item {
  font-size: 13px;
  color: var(--mall-text-2);
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
}
.sort-item.active {
  background: var(--mall-primary);
  color: #fff;
}
.guest-tip {
  margin-bottom: 14px;
}
.footer {
  text-align: center;
  padding: 28px 0 12px;
  color: var(--mall-text-3);
  font-size: 13px;
}
</style>
