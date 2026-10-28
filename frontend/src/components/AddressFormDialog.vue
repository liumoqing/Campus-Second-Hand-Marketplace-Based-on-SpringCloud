<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { userApi } from '@/api'
import { regions } from '@/data/regions'
import type { AddressItem } from '@/types'

const props = defineProps<{ modelValue: boolean; address?: AddressItem | null }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved', a: AddressItem): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const region = ref<string[]>([])

const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const a = props.address
    form.receiver = a?.receiver || ''
    form.phone = a?.phone || ''
    form.province = a?.province || ''
    form.city = a?.city || ''
    form.district = a?.district || ''
    form.detail = a?.detail || ''
    form.isDefault = !!a?.isDefault
    region.value = [form.province, form.city, form.district].filter(Boolean) as string[]
  }
)

function onRegion(v: string[]) {
  form.province = v?.[0] || ''
  form.city = v?.[1] || ''
  form.district = v?.[2] || ''
}

const rules = {
  receiver: [{ required: true, min: 2, max: 20, message: '收货人 2~20 字', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  detail: [{ required: true, min: 5, max: 100, message: '详细地址 5~100 字', trigger: 'blur' }]
}

async function save() {
  await formRef.value?.validate()
  if (!form.province || !form.city || !form.district) {
    ElMessage.warning('请选择省 / 市 / 区')
    return
  }
  saving.value = true
  try {
    if (props.address) {
      await userApi.updateAddress(props.address.id, { ...form })
      ElMessage.success('地址已更新')
    } else {
      const r = await userApi.createAddress({ ...form })
      ElMessage.success('地址已添加')
      emit('saved', { id: r.id, ...form } as AddressItem)
    }
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="address ? '编辑收货地址' : '新增收货地址'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
      <el-form-item label="收货人" prop="receiver">
        <el-input v-model="form.receiver" placeholder="请输入收货人姓名" maxlength="20" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="所在地区" required>
        <el-cascader
          v-model="region"
          :options="regions"
          :props="{ value: 'value', label: 'label' }"
          placeholder="省 / 市 / 区"
          style="width: 100%"
          @change="onRegion"
        />
      </el-form-item>
      <el-form-item label="详细地址" prop="detail">
        <el-input v-model="form.detail" type="textarea" :rows="2" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
