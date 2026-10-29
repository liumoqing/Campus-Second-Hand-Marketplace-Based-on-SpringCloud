<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi, paymentApi, productApi, userApi } from '@/api'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import { conditionLabel } from '@/utils/product'
import type { AddressItem, ProductDetail } from '@/types'

const route = useRoute()
const router = useRouter()

const id = computed(() => String(route.params.id || ''))
const product = ref<ProductDetail | null>(null)
const addresses = ref<AddressItem[]>([])
const selectedId = ref<string>('')
const remark = ref('')
const dialogVisible = ref(false)
const editing = ref<AddressItem | null>(null)
const submitting = ref(false)

const payVisible = ref(false)
const creating = ref(false)
const paying = ref(false)
const orderInfo = ref<{ orderNo: string; totalAmount: string; freight: string; payAmount: string; sameCity: boolean } | null>(null)

const selectedAddress = computed(() => addresses.value.find((a) => a.id === selectedId.value) || null)

const freightText = computed(() => {
  const p = product.value
  if (!p) return '-'
  if (p.freightMode === 'FREE_SHIPPING') return '包邮 ¥0.00'
  if (p.freightMode === 'SELF_PICKUP') return '仅自提（同城当场交易）¥0.00'
  return '按距离估算：同城 ¥5.00 / 跨城 ¥10.00（下单时结算）'
})

async function loadAddresses() {
  addresses.value = await userApi.addresses()
  if (!selectedId.value || !addresses.value.some((a) => a.id === selectedId.value)) {
    const def = addresses.value.find((a) => !!a.isDefault) || addresses.value[0]
    selectedId.value = def?.id || ''
  }
}

function addAddress() {
  editing.value = null
  dialogVisible.value = true
}

function editAddress(a: AddressItem) {
  editing.value = a
  dialogVisible.value = true
}

async function onSaved() {
  await loadAddresses()
}

async function createOrder() {
  if (!selectedAddress.value) {
    ElMessage.warning('请先选择收货地址')
    return
  }
  creating.value = true
  try {
    orderInfo.value = await orderApi.create({
      productId: id.value,
      addressId: selectedId.value,
      remark: remark.value || undefined
    })
    payVisible.value = true
  } finally {
    creating.value = false
  }
}

async function payNow() {
  if (!orderInfo.value) return
  paying.value = true
  try {
    await paymentApi.pay(orderInfo.value.orderNo)
    ElMessage.success('支付成功（模拟），等待卖家发货')
    payVisible.value = false
    router.push('/orders?tab=UNSHIPPED')
  } finally {
    paying.value = false
  }
}

function goOrders() {
  payVisible.value = false
  router.push('/orders?tab=UNPAID')
}

onMounted(async () => {
  product.value = await productApi.detail(id.value)
  await loadAddresses()
})
</script>

<template>
  <div class="purchase mall-page">
    <h2 class="page-title">确认订单</h2>

    <div class="section mall-card">
      <div class="section-head">
        <h3>收货地址</h3>
        <el-button link type="primary" @click="addAddress">+ 新增地址</el-button>
      </div>

      <div v-if="addresses.length" class="addr-scroll">
        <div
          v-for="a in addresses"
          :key="a.id"
          class="addr-card"
          :class="{ active: selectedId === a.id }"
          @click="selectedId = a.id"
        >
          <div class="addr-top">
            <b>{{ a.receiver }}</b>
            <span>{{ a.phone }}</span>
            <el-tag v-if="a.isDefault" size="small" type="warning">默认</el-tag>
          </div>
          <div class="addr-body">{{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}</div>
          <el-button link size="small" class="edit" @click.stop="editAddress(a)">编辑</el-button>
        </div>
        <div class="addr-card add-card" @click="addAddress">+<span>新增地址</span></div>
      </div>

      <el-empty v-else description="还没有收货地址，先添加一个">
        <el-button type="primary" @click="addAddress">新增地址</el-button>
      </el-empty>
    </div>

    <div class="section mall-card">
      <div class="section-head"><h3>订单信息</h3></div>
      <div v-if="product" class="goods">
        <img v-if="product.coverUrl" :src="product.coverUrl" alt="" />
        <div class="goods-info">
          <div class="g-title">{{ product.title }}</div>
          <div class="mall-sub">成色 {{ conditionLabel(product.conditionLevel) || '未填' }} · {{ product.city }}</div>
        </div>
        <div class="g-price mall-price">¥{{ product.price }}</div>
      </div>
      <div class="line"><span>运费</span><span>{{ freightText }}</span></div>
      <div class="line">
        <span>备注</span>
        <el-input v-model="remark" placeholder="选填，例如：周末面交" maxlength="100" class="remark" />
      </div>
      <div class="line total">
        <span>商品金额</span>
        <span class="mall-price">¥{{ product?.price || '0.00' }}</span>
      </div>
    </div>

    <div class="footer-bar">
      <el-button type="primary" size="large" :loading="creating" @click="createOrder">确认下单</el-button>
    </div>

    <AddressFormDialog v-model="dialogVisible" :address="editing" @saved="onSaved" />

    <el-dialog v-model="payVisible" title="模拟支付" width="420px" :close-on-click-modal="false">
      <div v-if="orderInfo" class="pay-box">
        <div class="pay-no">订单号 {{ orderInfo.orderNo }}</div>
        <div class="pay-amount">¥{{ orderInfo.payAmount }}</div>
        <div class="mall-sub">
          商品 ¥{{ orderInfo.totalAmount }} + 运费 ¥{{ orderInfo.freight }}
          （{{ orderInfo.sameCity ? '同城' : '跨城' }}）
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="这是模拟支付：点「立即支付」即视为支付成功，不会真的扣款。"
          class="pay-tip"
        />
      </div>
      <template #footer>
        <el-button @click="goOrders">稍后支付</el-button>
        <el-button type="primary" :loading="paying" @click="payNow">立即支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.purchase {
  padding: 24px;
  max-width: 980px;
}
.page-title {
  font-size: 20px;
  margin: 0 0 16px;
}
.section {
  padding: 18px 20px;
  margin-bottom: 16px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-head h3 {
  margin: 0;
  font-size: 15px;
}
.addr-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
}
.addr-card {
  flex: 0 0 280px;
  border: 1px solid var(--mall-border);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  position: relative;
  transition: border-color 0.15s;
}
.addr-card.active {
  border-color: var(--mall-primary);
  background: var(--mall-primary-light);
}
.addr-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.addr-body {
  margin-top: 6px;
  font-size: 13px;
  color: var(--mall-text-2);
  line-height: 1.5;
}
.edit {
  position: absolute;
  right: 6px;
  bottom: 4px;
}
.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--mall-text-3);
  border-style: dashed;
  gap: 4px;
}
.add-card span {
  font-size: 12px;
}
.goods {
  display: flex;
  gap: 12px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mall-border);
}
.goods img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px;
}
.goods-info {
  flex: 1;
  min-width: 0;
}
.g-title {
  font-size: 14px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.g-price {
  font-size: 18px;
}
.line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  color: var(--mall-text-2);
  gap: 16px;
}
.remark {
  max-width: 420px;
}
.total {
  border-top: 1px solid var(--mall-border);
  font-size: 14px;
}
.total .mall-price {
  font-size: 20px;
}
.footer-bar {
  display: flex;
  justify-content: flex-end;
}
.pay-box {
  text-align: center;
}
.pay-no {
  font-size: 12px;
  color: var(--mall-text-3);
}
.pay-amount {
  font-size: 32px;
  color: var(--mall-price);
  margin: 8px 0 4px;
  font-weight: 600;
}
.pay-tip {
  margin-top: 14px;
  text-align: left;
}
</style>
