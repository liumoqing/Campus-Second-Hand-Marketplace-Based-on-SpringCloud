<script setup lang="ts">
import type { ProductCard } from '@/types'
import { conditionLabel } from '@/utils/product'

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
      <span v-if="item.conditionLevel" class="condition-chip">{{ conditionLabel(item.conditionLevel) }}</span>
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
        <span>{{ conditionLabel(item.conditionLevel) || '成色未填' }}</span>
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
  border-color: rgba(58, 49, 39, 0.08);
  background: rgba(255, 253, 249, 0.96);
  box-shadow: 0 8px 26px rgba(72, 55, 37, 0.055);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}
.card:hover {
  transform: translateY(-6px);
  border-color: rgba(242, 107, 63, 0.18);
  box-shadow: 0 20px 42px rgba(72, 55, 37, 0.13);
}
.cover {
  position: relative;
  overflow: hidden;
  background: linear-gradient(145deg, #f6f1e9, #f1ece3);
}
.cover::after {
  position: absolute;
  inset: auto 0 0;
  height: 42%;
  background: linear-gradient(180deg, transparent, rgba(36, 35, 31, 0.12));
  content: '';
  pointer-events: none;
}
.cover img {
  width: 100%;
  display: block;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  transition: transform 0.42s cubic-bezier(0.2, 0.72, 0.25, 1);
}
.card:hover .cover img {
  transform: scale(1.035);
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
  right: 8px;
  z-index: 2;
  background: rgba(36, 35, 31, 0.72);
  color: #fff;
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 999px;
  backdrop-filter: blur(8px);
}
.condition-chip {
  position: absolute;
  left: 9px;
  bottom: 9px;
  z-index: 2;
  padding: 3px 9px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 999px;
  color: #fff;
  background: rgba(36, 35, 31, 0.36);
  font-size: 11px;
  font-weight: 650;
  backdrop-filter: blur(10px);
}
.body {
  padding: 13px 14px 14px;
}
.title {
  font-size: 15px;
  font-weight: 680;
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 8px;
  letter-spacing: -0.015em;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
}
.mall-price {
  font-size: 21px;
  line-height: 1.2;
}
.origin {
  font-size: 12px;
  color: var(--mall-text-3);
  text-decoration: line-through;
}
.meta {
  margin-top: 7px;
  font-size: 12px;
  color: var(--mall-text-3);
  display: flex;
  gap: 3px;
  flex-wrap: wrap;
}
.seller {
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px solid var(--mall-border);
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
