<script setup lang="ts">
import type { ProductCard } from '@/types'

defineProps<{ item: ProductCard }>()
const emit = defineEmits<{ (e: 'select', item: ProductCard): void }>()

function freightText(mode?: string) {
  if (mode === 'FREE_SHIPPING') return '包邮'
  if (mode === 'SELF_PICKUP') return '仅自提'
  return '按距离'
}
</script>

<template>
  <div class="card mall-card" @click="emit('select', item)">
    <div class="cover">
      <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" />
      <div v-else class="cover-empty">暂无图片</div>
      <span v-if="item.status === 'SOLD'" class="badge">已售出</span>
    </div>
    <div class="body">
      <div class="title">{{ item.title }}</div>
      <div class="price-row">
        <span class="mall-price">¥{{ item.price }}</span>
        <span v-if="item.originalPrice" class="origin">¥{{ item.originalPrice }}</span>
      </div>
      <div class="meta">
        <span>{{ item.city || '未知地区' }}</span>
        <span>·</span>
        <span>{{ item.conditionLevel ? `${item.conditionLevel}成新` : '成色未填' }}</span>
        <span>·</span>
        <span>{{ freightText(item.freightMode) }}</span>
      </div>
      <div class="seller">
        <el-avatar :size="20" :src="item.sellerAvatar || undefined">
          {{ (item.sellerNickname || '?').slice(0, 1) }}
        </el-avatar>
        <span class="seller-name">{{ item.sellerNickname || '匿名卖家' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}
.cover {
  position: relative;
  background: #f2f3f5;
}
.cover img {
  width: 100%;
  display: block;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}
.cover-empty {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mall-text-3);
  font-size: 13px;
}
.badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}
.body {
  padding: 10px 12px 12px;
}
.title {
  font-size: 14px;
  line-height: 1.45;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 6px;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.mall-price {
  font-size: 18px;
}
.origin {
  font-size: 12px;
  color: var(--mall-text-3);
  text-decoration: line-through;
}
.meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mall-text-3);
  display: flex;
  gap: 3px;
  flex-wrap: wrap;
}
.seller {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.seller-name {
  font-size: 12px;
  color: var(--mall-text-2);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
