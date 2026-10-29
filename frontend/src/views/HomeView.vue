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
  gap: 26px;
  padding: 28px 28px 80px;
  align-items: flex-start;
}
.cats {
  width: 232px;
  flex: 0 0 232px;
  position: sticky;
  top: 100px;
  background: rgba(255, 253, 249, 0.92);
  border: 1px solid var(--mall-border);
  border-radius: 22px;
  padding: 14px 12px;
  max-height: calc(100vh - 128px);
  overflow: auto;
  box-shadow: var(--mall-shadow-md);
  backdrop-filter: blur(16px);
}
.cats-title {
  padding: 4px 10px 14px;
  color: var(--mall-text);
  font-size: 15px;
  font-weight: 750;
  letter-spacing: -0.02em;
}
.cat {
  padding: 9px 12px;
  margin: 2px 0;
  font-size: 14px;
  color: var(--mall-text-2);
  border-radius: 12px;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}
.cat.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 620;
}
.cat-arrow {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--mall-text-3);
  transition: transform 0.18s ease, color 0.18s ease;
}
.cat.top.expanded .cat-arrow {
  color: var(--mall-primary);
  transform: rotate(0deg);
}
.cat.sub {
  margin-left: 16px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--mall-text-3);
}
.cat:hover {
  color: var(--mall-text);
  background: var(--mall-surface-muted);
  transform: translateX(2px);
}
.cat.active {
  color: var(--mall-primary);
  background: linear-gradient(135deg, #fff1e9 0%, #fff8f3 100%);
  font-weight: 700;
  box-shadow: inset 3px 0 0 var(--mall-primary);
}
.content {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 4px 22px;
  gap: 16px;
}
.result {
  font-size: 27px;
  font-weight: 800;
  letter-spacing: -0.045em;
  white-space: nowrap;
}
.count {
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  color: var(--mall-text-2);
  background: rgba(255, 253, 249, 0.8);
  box-shadow: inset 0 0 0 1px var(--mall-border);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  vertical-align: middle;
}
.sort {
  display: flex;
  gap: 4px;
  background: rgba(255, 253, 249, 0.86);
  border: 1px solid var(--mall-border);
  border-radius: 14px;
  padding: 5px;
  position: sticky;
  top: 100px;
  box-shadow: var(--mall-shadow-sm);
  backdrop-filter: blur(16px);
}
.sort-item {
  font-size: 13px;
  color: var(--mall-text-2);
  padding: 7px 11px;
  border-radius: 10px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}
.sort-item:hover {
  color: var(--mall-text);
  background: var(--mall-surface-muted);
}
.sort-item.active {
  background: linear-gradient(135deg, #f58258 0%, var(--mall-primary) 100%);
  color: #fff;
  box-shadow: 0 7px 16px rgba(242, 107, 63, 0.2);
}
.guest-tip {
  margin-bottom: 20px;
  border: 1px solid rgba(239, 179, 95, 0.28);
  border-radius: 15px;
  background: rgba(255, 249, 237, 0.8);
}
.footer {
  text-align: center;
  padding: 34px 0 12px;
  color: var(--mall-text-3);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .home {
    gap: 18px;
    padding: 20px 18px 64px;
  }
  .cats {
    width: 210px;
    flex-basis: 210px;
  }
  .result {
    font-size: 23px;
  }
}
</style>
