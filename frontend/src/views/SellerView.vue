<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import SellerCardComp from '@/components/SellerCard.vue'
import ProductCardItem from '@/components/ProductCardItem.vue'
import type { ProductCard, SellerCard } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const seller = ref<SellerCard | null>(null)
const products = ref<ProductCard[]>([])
const loading = ref(false)

const id = computed(() => String(route.params.id || ''))

async function load() {
  loading.value = true
  try {
    seller.value = await userApi.sellerCard(id.value)
    const page = await productApi.page({ sellerId: id.value, pageNum: 1, pageSize: 40 } as any)
    products.value = page.records
  } finally {
    loading.value = false
  }
}

function openProduct(p: ProductCard) {
  router.push(`/product/${p.id}`)
}

watch(id, load)
onMounted(load)
</script>

<template>
  <div class="seller-page mall-page" v-loading="loading">
    <SellerCardComp v-if="seller" :seller="seller" />
    <div v-if="seller && seller.id === user.profile?.id" class="self-tip mall-sub">
      这是你自己的主页，别人看到的就是这个样子。
    </div>

    <div class="section">
      <h3 class="sec-title">在售闲置（{{ products.length }}）</h3>
      <div v-if="products.length" class="grid">
        <ProductCardItem v-for="p in products" :key="p.id" :item="p" @select="openProduct" />
      </div>
      <el-empty v-else description="这位卖家暂时没有在售商品" />
    </div>
  </div>
</template>

<style scoped>
.seller-page {
  padding: 24px;
  max-width: 1200px;
}
.self-tip {
  margin: 10px 4px 0;
  font-size: 12px;
}
.section {
  margin-top: 20px;
}
.sec-title {
  font-size: 16px;
  margin: 0 0 14px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
</style>
