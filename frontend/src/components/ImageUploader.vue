<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { productApi } from '@/api'

const props = withDefaults(defineProps<{ modelValue: string[]; limit?: number; bizType?: string }>(), {
  limit: 5,
  bizType: 'product'
})
const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function pick() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const r = await productApi.upload(file, props.bizType)
    emit('update:modelValue', [...props.modelValue, r.url])
  } catch {
    /* 拦截器已提示 */
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function remove(i: number) {
  const next = [...props.modelValue]
  next.splice(i, 1)
  emit('update:modelValue', next)
}
</script>

<template>
  <input ref="fileInput" class="file-input" type="file"
         accept="image/jpeg,image/png,image/webp,image/gif" @change="onFileChange" />
  <div class="uploader">
    <div v-for="(url, i) in modelValue" :key="url" class="item">
      <img :src="url" alt="" />
      <span class="del" @click="remove(i)">×</span>
      <span v-if="i === 0" class="first">封面</span>
    </div>

    <div v-if="modelValue.length < limit" class="add" @click="pick">
      <el-icon v-if="!uploading"><Plus /></el-icon>
      <span>{{ uploading ? '上传中…' : '添加图片' }}</span>
      <small>{{ modelValue.length }}/{{ limit }}</small>
    </div>
  </div>
</template>

<style scoped>
.file-input {
  display: none;
}

.uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.item,
.add {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  position: relative;
  overflow: hidden;
}
.item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.del {
  position: absolute;
  top: 2px;
  right: 4px;
  width: 18px;
  height: 18px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
.first {
  position: absolute;
  left: 0;
  bottom: 0;
  font-size: 11px;
  color: #fff;
  background: var(--mall-primary);
  padding: 1px 6px;
  border-top-right-radius: 6px;
}
.add {
  border: 1px dashed var(--mall-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--mall-text-3);
  cursor: pointer;
  font-size: 12px;
  background: #fafbfc;
}
.add small {
  font-size: 11px;
}
</style>
