<script setup lang="ts">
import type { SellerCard } from '@/types'

defineProps<{ seller: SellerCard }>()
const emit = defineEmits<{ (e: 'open', id: string): void }>()
</script>

<template>
  <div class="seller-card mall-card" @click="emit('open', seller.id)">
    <el-avatar :size="56" :src="seller.avatar || undefined">
      {{ (seller.nickname || '?').slice(0, 1) }}
    </el-avatar>
    <div class="info">
      <div class="row1">
        <span class="name">{{ seller.nickname }}</span>
        <span class="city">{{ seller.city || '未填写地区' }}</span>
      </div>
      <div class="row2">
        <span>注册 {{ seller.registerDays ?? 0 }} 天</span>
        <span>卖出 {{ seller.soldCount ?? 0 }} 件</span>
        <span>在售 {{ seller.onSaleCount ?? 0 }} 件</span>
        <span>
          评分
          <b class="rating">{{ seller.avgRating ?? '暂无' }}</b>
          <template v-if="seller.reviewCount">（{{ seller.reviewCount }}）</template>
        </span>
        <span>上次登录 {{ seller.lastLoginTime || '未知' }}</span>
      </div>
    </div>
    <el-button size="small" plain>进主页</el-button>
  </div>
</template>

<style scoped>
.seller-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  cursor: pointer;
}
.info {
  flex: 1;
  min-width: 0;
}
.row1 {
  display: flex;
  align-items: center;
  gap: 10px;
}
.name {
  font-size: 16px;
  font-weight: 600;
}
.city {
  font-size: 12px;
  color: var(--mall-text-3);
}
.row2 {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--mall-text-2);
}
.rating {
  color: #ff9500;
}
</style>
