<template>
  <div class="checkout-page">
    <!-- 收货地址 -->
    <el-card class="block">
      <template #header><b>收货地址</b></template>
      <div class="addr-grid">
        <div v-for="a in addresses" :key="a.id"
             class="addr-card" :class="{ active: selectedAddrId === a.id }"
             @click="selectedAddrId = a.id">
          <div class="addr-line1">
            <b>{{ a.receiverName }}</b>
            <el-tag v-if="a.isDefault === 1" size="small" type="danger">默认</el-tag>
          </div>
          <div class="addr-line2">{{ a.receiverPhone }}</div>
          <div class="addr-line3">{{ a.province }}{{ a.city }}{{ a.district }}{{ a.detailAddress }}</div>
          <el-button v-if="a.isDefault !== 1" link type="primary" size="small" @click.stop="makeDefault(a)">设为默认</el-button>
        </div>
        <div class="addr-add" @click="openAddrDialog">
          <el-icon size="28"><Plus /></el-icon>
          <div>新增地址</div>
        </div>
      </div>
    </el-card>

    <!-- 商品清单 -->
    <el-card class="block">
      <template #header><b>商品清单（{{ lines.length }} 件）</b></template>
      <div class="line" v-for="(l, idx) in lines" :key="idx">
        <el-image :src="l.image || placeholder" fit="cover" class="l-img" />
        <div class="l-info">
          <div class="l-name">{{ l.productName }}</div>
          <div class="l-sku">{{ l.skuName }}</div>
        </div>
        <span class="l-price">¥{{ l.price }}</span>
        <span class="l-qty">×{{ l.quantity }}</span>
        <span class="l-sub">¥{{ (l.price * l.quantity).toFixed(2) }}</span>
      </div>
      <div class="sum-row">合计：<span class="sum">¥{{ totalAmount.toFixed(2) }}</span></div>
    </el-card>

    <!-- 备注与提交 -->
    <el-card class="block">
      <el-input v-model="remark" placeholder="订单备注（选填）" maxlength="200" />
      <div class="submit-row">
        <span class="pay-tip">模拟支付：下单后订单列表「去支付」</span>
        <el-button type="danger" size="large" :loading="submitting" :disabled="!selectedAddrId || !lines.length"
                   @click="submit">提交订单</el-button>
      </div>
    </el-card>

    <!-- 新增/编辑地址弹窗 -->
    <el-dialog v-model="addrDialog" title="新增收货地址" width="480px">
      <el-form :model="addrForm" label-width="80px">
        <el-form-item label="收货人"><el-input v-model="addrForm.receiverName" placeholder="姓名" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="addrForm.receiverPhone" placeholder="手机号" /></el-form-item>
        <el-form-item label="省市区">
          <el-input v-model="addrForm.province" placeholder="省" style="width: 32%" />
          <el-input v-model="addrForm.city" placeholder="市" style="width: 32%; margin: 0 2%" />
          <el-input v-model="addrForm.district" placeholder="区/县" style="width: 32%" />
        </el-form-item>
        <el-form-item label="详细地址"><el-input v-model="addrForm.detailAddress" placeholder="街道、门牌号等" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="addrForm.isDefault" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAddressList, addAddress, setDefaultAddress } from '../api/address'
import { getCartList } from '../api/cart'
import { getProductDetail } from '../api/product'
import { createOrder } from '../api/order'

const route = useRoute()
const router = useRouter()
const addresses = ref([])
const selectedAddrId = ref(null)
const lines = ref([])
const remark = ref('')
const submitting = ref(false)
const placeholder = 'https://via.placeholder.com/80'
const addrDialog = ref(false)
const addrForm = ref({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 })

const totalAmount = computed(() => lines.value.reduce((s, l) => s + l.price * l.quantity, 0))

async function loadAddresses() {
  addresses.value = await getAddressList()
  const def = addresses.value.find((a) => a.isDefault === 1)
  if (def) selectedAddrId.value = def.id
  else if (addresses.value.length) selectedAddrId.value = addresses.value[0].id
}

async function loadLines() {
  if (route.query.from === 'cart') {
    const all = await getCartList()
    lines.value = all
      .filter((i) => i.isSelected)
      .map((i) => ({ productId: i.productId, skuId: i.skuId, productName: i.productName, skuName: i.skuName, price: i.price, quantity: i.quantity, image: i.mainImage }))
  } else {
    // 立即购买：productId + skuId + quantity 从商品详情页带入
    const detail = await getProductDetail(route.query.productId)
    const sku = detail.skus.find((s) => s.id === Number(route.query.skuId)) || detail.skus[0]
    lines.value = [{
      productId: detail.product.id,
      skuId: sku.id,
      productName: detail.product.productName,
      skuName: sku.skuName,
      price: sku.price,
      quantity: Number(route.query.quantity) || 1,
      image: detail.product.mainImage
    }]
  }
}

function openAddrDialog() {
  addrForm.value = { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 }
  addrDialog.value = true
}

async function saveAddress() {
  const f = addrForm.value
  if (!f.receiverName || !f.receiverPhone || !f.detailAddress) {
    ElMessage.warning('请填写收货人、电话和详细地址')
    return
  }
  const id = await addAddress(f)
  ElMessage.success('地址已保存')
  addrDialog.value = false
  await loadAddresses()
  selectedAddrId.value = id
}

async function makeDefault(a) {
  await setDefaultAddress(a.id)
  await loadAddresses()
}

async function submit() {
  submitting.value = true
  try {
    const payload = {
      addressId: selectedAddrId.value,
      fromCart: route.query.from === 'cart',
      remark: remark.value
    }
    if (payload.fromCart) {
      payload.itemList = null
    } else {
      payload.itemList = lines.value.map((l) => ({ skuId: l.skuId, quantity: l.quantity }))
    }
    const orders = await createOrder(payload)
    const count = Array.isArray(orders) ? orders.length : 1
    ElMessage.success(`下单成功，共 ${count} 笔订单（已按店铺分单），请尽快支付`)
    router.push('/orders')
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadAddresses(), loadLines()])
})
</script>

<style scoped>
.checkout-page { max-width: 1000px; margin: 0 auto; }
.block { margin-bottom: 16px; }
.addr-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.addr-card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; cursor: pointer; }
.addr-card.active { border-color: #dc2626; background: #fff5f5; }
.addr-line1 { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.addr-line2 { color: #64748b; font-size: 13px; margin-bottom: 4px; }
.addr-line3 { color: #94a3b8; font-size: 12px; line-height: 1.5; }
.addr-add { border: 1px dashed #cbd5e1; border-radius: 10px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #94a3b8; cursor: pointer; min-height: 110px; }
.line { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f8fafc; }
.l-img { width: 60px; height: 60px; border-radius: 8px; }
.l-info { flex: 1; }
.l-name { font-weight: 600; }
.l-sku { color: #94a3b8; font-size: 12px; margin-top: 4px; }
.l-price { color: #64748b; width: 80px; text-align: right; }
.l-qty { width: 60px; text-align: center; color: #64748b; }
.l-sub { width: 100px; text-align: right; font-weight: 600; }
.sum-row { text-align: right; padding-top: 12px; font-weight: 600; }
.sum { color: #dc2626; font-size: 20px; font-weight: 800; }
.submit-row { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pay-tip { color: #94a3b8; font-size: 13px; }
</style>
