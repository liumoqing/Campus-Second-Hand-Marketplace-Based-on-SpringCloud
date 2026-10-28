<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const menus = [
  { path: '/admin/users', label: '用户管理', icon: 'User' },
  { path: '/admin/products', label: '商品管理', icon: 'Goods' },
  { path: '/admin/categories', label: '分类管理', icon: 'Menu' },
  { path: '/admin/orders', label: '订单与退款', icon: 'Tickets' }
]

const active = computed(() => route.path)
</script>

<template>
  <div class="admin mall-page">
    <aside class="side">
      <div class="side-title">管理后台</div>
      <div
        v-for="m in menus"
        :key="m.path"
        class="side-item"
        :class="{ active: active.startsWith(m.path) }"
        @click="router.push(m.path)"
      >
        {{ m.label }}
      </div>
      <div class="side-foot mall-sub">
        {{ user.profile?.nickname }} ·
        {{ user.isSuperAdmin ? '超级管理员' : '普通管理员' }}
      </div>
    </aside>
    <section class="content">
      <router-view />
    </section>
  </div>
</template>

<style scoped>
.admin {
  padding: 20px 24px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.side {
  width: 180px;
  flex: 0 0 180px;
  background: #fff;
  border-radius: 8px;
  padding: 12px 8px;
  position: sticky;
  top: 84px;
}
.side-title {
  font-size: 13px;
  color: var(--mall-text-3);
  padding: 4px 10px 10px;
}
.side-item {
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--mall-text-2);
  cursor: pointer;
}
.side-item:hover {
  background: #f5f6f8;
}
.side-item.active {
  background: var(--mall-primary-light);
  color: var(--mall-primary);
  font-weight: 600;
}
.side-foot {
  margin-top: 14px;
  padding: 0 12px;
  font-size: 12px;
  line-height: 1.6;
}
.content {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 18px 20px;
}
</style>
