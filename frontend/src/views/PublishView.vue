<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { productApi } from '@/api'
import { regions } from '@/data/regions'
import ImageUploader from '@/components/ImageUploader.vue'
import SplitButton from '@/components/SplitButton.vue'
import type { CategoryNode } from '@/types'

const route = useRoute()
const router = useRouter()

const editId = computed(() => (route.params.id ? String(route.params.id) : ''))
const isEdit = computed(() => !!editId.value)

const formRef = ref<FormInstance>()
const categories = ref<CategoryNode[]>([])
const region = ref<string[]>([])
const saving = ref(false)
const savingDraft = ref(false)
/** 编辑时的原始状态：草稿/已下架的商品，编辑页点「发布」要额外调 P9/P8 才真正上架 */
const originStatus = ref('')

const form = reactive({
  images: [] as string[],
  title: '',
  categoryId: '',
  price: undefined as number | undefined,
  originalPrice: undefined as number | undefined,
  conditionLevel: 9,
  freightMode: 'DISTANCE',
  province: '',
  city: '',
  district: '',
  description: ''
})

const categoryOptions = computed(() =>
  categories.value.map((c) => ({
    value: c.id,
    label: c.name,
    children: (c.children || []).map((s) => ({ value: s.id, label: s.name }))
  }))
)

const categoryPath = ref<string[]>([])

const freightOptions = [
  { value: 'FREE_SHIPPING', label: '包邮', desc: '运费我出，买家不额外付款' },
  { value: 'DISTANCE', label: '按距离估算', desc: '同城 5 元，跨城 10 元' },
  { value: 'SELF_PICKUP', label: '仅自提', desc: '同城当面交易，不收运费' }
]

const rules = {
  title: [{ required: true, min: 4, max: 60, message: '标题 4~60 字', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请填写价格', trigger: 'blur' }]
}

function onRegion(v: string[]) {
  form.province = v?.[0] || ''
  form.city = v?.[1] || ''
  form.district = v?.[2] || ''
}

function onCategory(v: string[]) {
  form.categoryId = v?.[v.length - 1] || ''
}

function buildPayload(status: 'ON_SALE' | 'DRAFT') {
  return {
    title: form.title,
    categoryId: form.categoryId || undefined,
    price: form.price,
    originalPrice: form.originalPrice,
    conditionLevel: form.conditionLevel,
    freightMode: form.freightMode,
    province: form.province || undefined,
    city: form.city || undefined,
    district: form.district || undefined,
    description: form.description || undefined,
    images: form.images,
    status
  }
}

/** 完整校验（发布） */
async function submit() {
  await formRef.value?.validate()
  if (!form.images.length) {
    ElMessage.warning('至少上传 1 张图片')
    return
  }
  if (!form.province || !form.city) {
    ElMessage.warning('请选择发货地')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await productApi.update(editId.value, buildPayload('ON_SALE'))
      // P5 只改内容不改状态，草稿/已下架的商品要显式走 P9/P8 才会上架
      if (originStatus.value === 'DRAFT') {
        await productApi.publishDraft(editId.value)
      } else if (originStatus.value === 'OFF_SHELF') {
        await productApi.onShelf(editId.value)
      }
      ElMessage.success('已保存')
    } else {
      await productApi.create(buildPayload('ON_SALE'))
      ElMessage.success('发布成功')
    }
    router.push('/mine/published')
  } finally {
    saving.value = false
  }
}

/** 存草稿：只校验标题（与后端约定一致） */
async function saveDraft() {
  if (!form.title || form.title.length < 4) {
    ElMessage.warning('先填个标题（4~60 字）再存草稿')
    return
  }
  savingDraft.value = true
  try {
    if (isEdit.value) {
      await productApi.update(editId.value, buildPayload('DRAFT'))
      ElMessage.success('草稿已保存')
    } else {
      await productApi.create(buildPayload('DRAFT'))
      ElMessage.success('草稿已保存，可在「我的-草稿箱」继续编辑')
    }
    router.push('/mine/drafts')
  } finally {
    savingDraft.value = false
  }
}

onMounted(async () => {
  categories.value = await productApi.categories()
  if (isEdit.value) {
    const d = await productApi.detail(editId.value)
    originStatus.value = d.status || ''
    form.images = d.images?.length ? [...d.images] : d.coverUrl ? [d.coverUrl] : []
    form.title = d.title
    form.categoryId = d.categoryId || ''
    form.price = Number(d.price)
    form.originalPrice = d.originalPrice ? Number(d.originalPrice) : undefined
    form.conditionLevel = d.conditionLevel || 9
    form.freightMode = d.freightMode || 'DISTANCE'
    form.province = d.province || ''
    form.city = d.city || ''
    form.district = d.district || ''
    form.description = d.description || ''
    region.value = [form.province, form.city, form.district].filter(Boolean) as string[]
    const cat = categories.value.find((c) => c.id === d.categoryId)
    if (cat) categoryPath.value = [cat.id]
    else {
      const parent = categories.value.find((c) => (c.children || []).some((s) => s.id === d.categoryId))
      if (parent) categoryPath.value = [parent.id, d.categoryId as string]
    }
  }
})
</script>

<template>
  <div class="publish mall-page">
    <h2 class="page-title">{{ isEdit ? '编辑闲置' : '发布闲置' }}</h2>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form mall-card">
      <el-form-item label="商品图片（1~5 张，第一张为封面）" required>
        <ImageUploader v-model="form.images" :limit="5" biz-type="product" />
      </el-form-item>

      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：iPhone 13 128G 蓝色 自用无磕碰" />
      </el-form-item>

      <el-form-item label="分类" prop="categoryId">
        <el-cascader
          v-model="categoryPath"
          :options="categoryOptions"
          :props="{ value: 'value', label: 'label' }"
          placeholder="选择分类（支持二级）"
          style="width: 100%"
          @change="onCategory"
        />
      </el-form-item>

      <div class="grid-3">
        <el-form-item label="价格（元）" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="原价（选填）">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="成色">
          <el-select v-model="form.conditionLevel">
            <el-option v-for="n in 9" :key="n" :value="10 - n" :label="`${10 - n} 成新`" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="发货方式">
        <div class="freight">
          <div
            v-for="f in freightOptions"
            :key="f.value"
            class="freight-item"
            :class="{ active: form.freightMode === f.value }"
            @click="form.freightMode = f.value"
          >
            <b>{{ f.label }}</b>
            <span>{{ f.desc }}</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="发货地（省市必填）">
        <el-cascader
          v-model="region"
          :options="regions"
          :props="{ value: 'value', label: 'label' }"
          placeholder="省 / 市 / 区"
          style="width: 100%"
          @change="onRegion"
        />
      </el-form-item>

      <el-form-item label="描述（选填）">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="入手时间、使用情况、配件、瑕疵、交易方式…"
        />
      </el-form-item>

      <div class="submit-row">
        <SplitButton
      :primary-text="isEdit ? '保存并发布' : '发布'"
      :show-draft="!isEdit"
      :loading="saving"
      :loading-draft="savingDraft"
      @primary="submit"
      @draft="saveDraft"
    />
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.publish {
  padding: 24px;
  max-width: 760px;
}
.page-title {
  font-size: 20px;
  margin: 0 0 16px;
}
.form {
  padding: 20px 24px;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.freight {
  display: flex;
  gap: 12px;
  width: 100%;
}
.freight-item {
  flex: 1;
  border: 1px solid var(--mall-border);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.freight-item b {
  font-size: 14px;
}
.freight-item span {
  font-size: 12px;
  color: var(--mall-text-3);
}
.freight-item.active {
  border-color: var(--mall-primary);
  background: var(--mall-primary-light);
}
.submit-row {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
