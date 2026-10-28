<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api'
import type { CategoryNode } from '@/types'

const tree = ref<CategoryNode[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<any>(null)
const form = reactive({ name: '', parentId: '' as string, sort: 0 })

async function load() {
  loading.value = true
  try {
    tree.value = await productApi.categories(true)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: CategoryNode) {
  editing.value = null
  form.name = ''
  form.parentId = parent ? parent.id : ''
  form.sort = 0
  dialogVisible.value = true
}

function openEdit(node: any) {
  editing.value = node
  form.name = node.name
  form.parentId = ''
  form.sort = node.sort ?? 0
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名')
    return
  }
  if (editing.value) {
    await productApi.updateCategory(editing.value.id, { name: form.name, sort: form.sort })
    ElMessage.success('已更新')
  } else {
    await productApi.createCategory({
      name: form.name,
      parentId: form.parentId || undefined,
      sort: form.sort
    })
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  load()
}

async function remove(node: any) {
  await ElMessageBox.confirm(`删除分类「${node.name}」？分类下有商品时不允许删除。`, '删除分类', { type: 'warning' })
  await productApi.deleteCategory(node.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="admin-cats">
    <div class="head">
      <h2 class="page-title">分类管理</h2>
      <el-button type="primary" @click="openCreate()">新建一级分类</el-button>
    </div>
    <p class="mall-sub">平台最多两级分类；首页左侧分类栏的顺序由「排序」决定，数值越小越靠前。</p>

    <el-table :data="tree" v-loading="loading" row-key="id" default-expand-all>
      <el-table-column label="分类名" min-width="240">
        <template #default="{ row }">
          <span :class="{ 'top-level': !row.parentId }">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button v-if="!row.parentId" link size="small" type="primary" @click="openCreate(row)">加子分类</el-button>
          <el-button link size="small" @click="openEdit(row)">重命名</el-button>
          <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新建分类'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="分类名">
          <el-input v-model="form.name" maxlength="20" placeholder="例如：数码电子" />
        </el-form-item>
        <el-form-item v-if="!editing" label="父分类">
          <el-select v-model="form.parentId" placeholder="不选则创建一级分类" clearable style="width: 100%">
            <el-option v-for="c in tree" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-title {
  font-size: 18px;
  margin: 0 0 6px;
}
.top-level {
  font-weight: 600;
}
</style>
