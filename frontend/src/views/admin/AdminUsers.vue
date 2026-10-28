<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminUserApi } from '@/api'
import { useUserStore } from '@/stores/user'

const user = useUserStore()

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)

const query = reactive({
  keyword: '',
  role: '',
  status: '',
  canPublish: '' as '' | number
})

const banVisible = ref(false)
const banTarget = ref<any>(null)
const banForm = reactive({ permanent: false, days: 7, reason: '' })

const pubVisible = ref(false)
const pubTarget = ref<any>(null)
const pubForm = reactive({ canPublish: false, permanent: false, days: 7 })

const canOperate = (row: any) => row.role !== 'SUPER_ADMIN' && row.id !== user.profile?.id
const canEditRole = (row: any) => user.isSuperAdmin && row.role !== 'SUPER_ADMIN' && row.id !== user.profile?.id

async function load() {
  loading.value = true
  try {
    const page = await adminUserApi.page({
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status || undefined,
      canPublish: query.canPublish === '' ? undefined : Number(query.canPublish),
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    rows.value = page.records
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function openBan(row: any) {
  banTarget.value = row
  banForm.permanent = false
  banForm.days = 7
  banForm.reason = ''
  banVisible.value = true
}

function banUntilText() {
  if (banForm.permanent) return '9999-12-31 23:59:59'
  const d = new Date(Date.now() + banForm.days * 86400000)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function submitBan() {
  if (!banTarget.value) return
  await adminUserApi.ban(banTarget.value.id, {
    permanent: banForm.permanent,
    banUntil: banForm.permanent ? undefined : banUntilText(),
    reason: banForm.reason || undefined
  })
  ElMessage.success('已封禁，该用户的在线会话会被立即踢下线')
  banVisible.value = false
  load()
}

async function unban(row: any) {
  await ElMessageBox.confirm(`解封 ${row.nickname}？对方需要重新登录。`, '确认解封')
  await adminUserApi.unban(row.id)
  ElMessage.success('已解封')
  load()
}

function openPublish(row: any) {
  pubTarget.value = row
  pubForm.canPublish = !row.canPublish
  pubForm.permanent = false
  pubForm.days = 7
  pubVisible.value = true
}

async function submitPublish() {
  if (!pubTarget.value) return
  await adminUserApi.publishPermission(pubTarget.value.id, {
    canPublish: pubForm.canPublish,
    permanent: pubForm.permanent,
    publishBanUntil: pubForm.canPublish || pubForm.permanent ? undefined : banUntilText()
  })
  ElMessage.success('已更新上架权限')
  pubVisible.value = false
  load()
}

async function changeRole(row: any, role: string) {
  await ElMessageBox.confirm(
    `把 ${row.nickname} 设为${role === 'ADMIN' ? '普通管理员' : '普通用户'}？该用户会被强制重新登录。`,
    '设置角色'
  )
  await adminUserApi.setRole(row.id, role)
  ElMessage.success('角色已更新')
  load()
}

const roleText: Record<string, string> = { USER: '普通用户', ADMIN: '管理员', SUPER_ADMIN: '超级管理员' }

onMounted(load)
</script>

<template>
  <div class="admin-users">
    <h2 class="page-title">用户管理</h2>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="用户名 / 邮箱 / 昵称 / 手机号" clearable style="width: 240px" @keyup.enter="search" />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 130px">
        <el-option value="USER" label="普通用户" />
        <el-option value="ADMIN" label="管理员" />
        <el-option value="SUPER_ADMIN" label="超级管理员" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option value="NORMAL" label="正常" />
        <el-option value="BANNED" label="已封禁" />
      </el-select>
      <el-select v-model="query.canPublish" placeholder="上架权限" clearable style="width: 130px">
        <el-option :value="1" label="可上架" />
        <el-option :value="0" label="已限制" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="u-cell">
            <el-avatar :size="32" :src="row.avatar || undefined">{{ row.nickname?.slice(0, 1) }}</el-avatar>
            <div>
              <div>{{ row.nickname }}</div>
              <div class="mall-sub">@{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'SUPER_ADMIN' ? 'danger' : row.role === 'ADMIN' ? 'warning' : 'info'">
            {{ roleText[row.role] || row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="170">
        <template #default="{ row }">
          <div v-if="row.status === 'BANNED'">
            <el-tag size="small" type="danger">已封禁</el-tag>
            <div class="mall-sub">至 {{ row.banUntil }}</div>
          </div>
          <el-tag v-else size="small" type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上架权限" width="140">
        <template #default="{ row }">
          <el-tag size="small" :type="row.canPublish ? 'success' : 'warning'">
            {{ row.canPublish ? '可上架' : '已限制' }}
          </el-tag>
          <div v-if="!row.canPublish" class="mall-sub">至 {{ row.publishBanUntil }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="creditScore" label="信用分" width="90" />
      <el-table-column prop="onSaleCount" label="在售" width="80" />
      <el-table-column prop="soldCount" label="卖出" width="80" />
      <el-table-column prop="lastLoginTime" label="最后登录" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="canOperate(row)">
            <el-button v-if="row.status === 'BANNED'" link size="small" @click="unban(row)">解封</el-button>
            <el-button v-else link size="small" type="danger" @click="openBan(row)">封禁</el-button>
            <el-button link size="small" @click="openPublish(row)">
              {{ row.canPublish ? '限制上架' : '恢复上架' }}
            </el-button>
            <el-dropdown v-if="canEditRole(row)" trigger="click" @command="(r: string) => changeRole(row, r)">
              <el-button link size="small" type="primary">设置角色</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="USER">设为普通用户</el-dropdown-item>
                  <el-dropdown-item command="ADMIN">设为普通管理员</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <span v-else class="mall-sub">不可操作</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="banVisible" title="封禁用户" width="440px">
      <el-form label-width="90px">
        <el-form-item label="封禁时长">
          <el-radio-group v-model="banForm.permanent">
            <el-radio :value="false">定时解封</el-radio>
            <el-radio :value="true">永久封禁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!banForm.permanent" label="天数">
          <el-input-number v-model="banForm.days" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="解封时间">
          <span class="mall-sub">{{ banUntilText() }}</span>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="banForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="danger" @click="submitBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pubVisible" title="上架权限" width="440px">
      <el-form label-width="100px">
        <el-form-item label="当前操作">
          <span>{{ pubForm.canPublish ? '恢复上架权限' : '限制上架权限' }}</span>
        </el-form-item>
        <template v-if="!pubForm.canPublish">
          <el-form-item label="时长">
            <el-radio-group v-model="pubForm.permanent">
              <el-radio :value="false">定时恢复</el-radio>
              <el-radio :value="true">永久限制</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!pubForm.permanent" label="天数">
            <el-input-number v-model="pubForm.days" :min="1" :max="365" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="pubVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPublish">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 18px;
  margin: 0 0 14px;
}
.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.u-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
