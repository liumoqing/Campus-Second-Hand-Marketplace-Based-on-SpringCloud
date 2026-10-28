<script setup lang="ts">
const props = withDefaults(
  defineProps<{ modelValue?: number; readonly?: boolean; size?: number }>(),
  { modelValue: 0, readonly: true, size: 18 }
)
const emit = defineEmits<{ (e: 'update:modelValue', v: number): void }>()

function pick(i: number) {
  if (props.readonly) return
  emit('update:modelValue', i)
}
</script>

<template>
  <div class="stars" :style="{ fontSize: `${size}px` }">
    <span
      v-for="i in 5"
      :key="i"
      class="star"
      :class="{ on: i <= (modelValue || 0), clickable: !readonly }"
      @click="pick(i)"
    >
      ★
    </span>
  </div>
</template>

<style scoped>
.stars {
  display: inline-flex;
  gap: 2px;
  line-height: 1;
}
.star {
  color: #dcdfe6;
}
.star.on {
  color: #ffb800;
}
.star.clickable {
  cursor: pointer;
}
</style>
