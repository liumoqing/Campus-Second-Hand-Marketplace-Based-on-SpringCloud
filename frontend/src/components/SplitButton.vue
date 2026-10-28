<script setup lang="ts">
withDefaults(
  defineProps<{
    primaryText?: string
    draftText?: string
    loading?: boolean
    loadingDraft?: boolean
    showDraft?: boolean
  }>(),
  { primaryText: '发布', draftText: '存草稿', showDraft: true }
)

const emit = defineEmits<{ (e: 'primary'): void; (e: 'draft'): void }>()
</script>

<template>
  <div class="split-btn">
    <button class="half primary" :disabled="loading" @click="emit('primary')">
      {{ loading ? '提交中…' : primaryText }}
    </button>
    <template v-if="showDraft">
      <span class="divider" />
      <button class="half draft" :disabled="loadingDraft" @click="emit('draft')">
        {{ loadingDraft ? '保存中…' : draftText }}
      </button>
    </template>
  </div>
</template>

<style scoped>
.split-btn {
  display: inline-flex;
  align-items: stretch;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.25);
}
.half {
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 0 22px;
  height: 42px;
  color: #fff;
  background: var(--mall-primary);
}
.half.draft {
  background: #fff;
  color: var(--mall-primary);
  border: 1px solid var(--mall-primary);
  border-left: none;
}
.divider {
  width: 1px;
  background: rgba(255, 255, 255, 0.35);
}
.half:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
