<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ items: any[]; columns?: number; gap?: number }>(), {
  columns: 5,
  gap: 16
})

/** 瀑布流：先按最矮列优先分配到各列，保证视觉均衡（CSS column-count 会按列切分导致顺序错乱） */
const buckets = ref<{ ids: number[]; height: number }[]>([])

const columnCount = computed(() => props.columns)

function distribute() {
  const cols: { ids: number[]; height: number }[] = Array.from({ length: columnCount.value }, () => ({
    ids: [],
    height: 0
  }))
  props.items.forEach((item, index) => {
    const target = cols.reduce((min, cur) => (cur.height < min.height ? cur : min), cols[0])
    // 无封面图时按较短高度估算，避免整列被一张图拉长
    const estimate = item.coverUrl ? 260 : 180
    target.ids.push(index)
    target.height += estimate
  })
  buckets.value = cols
}

watch(() => [props.items, props.columns], distribute, { immediate: true, deep: false })

const cols = computed(() => buckets.value)
</script>

<template>
  <div class="waterfall" :style="{ gap: `${gap}px` }">
    <div v-for="(col, ci) in cols" :key="ci" class="waterfall-col" :style="{ gap: `${gap}px` }">
      <slot v-for="idx in col.ids" :key="items[idx].id" :item="items[idx]" :index="idx" />
    </div>
  </div>
</template>

<style scoped>
.waterfall {
  display: flex;
  align-items: flex-start;
}
.waterfall-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
</style>
