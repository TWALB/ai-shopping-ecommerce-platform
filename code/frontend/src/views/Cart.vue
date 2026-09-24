<template>
  <div class="cart-page">
    <el-card>
      <template #header><b>我的购物车（{{ items.length }}）</b></template>

      <el-empty v-if="!items.length" description="购物车还是空的，去逛逛吧">
        <el-button type="primary" @click="$router.push('/products')">去逛逛</el-button>
      </el-empty>

      <template v-else>
        <!-- 表头 -->
        <div class="cart-head">
          <el-checkbox :model-value="allSelected" @change="toggleAll">全选</el-checkbox>
          <span class="h-item">商品信息</span>
          <span class="h-price">单价</span>
          <span class="h-qty">数量</span>
          <span class="h-sub">小计</span>
          <span class="h-op">操作</span>
        </div>

        <!-- 行 -->
        <div class="cart-row" v-for="item in items" :key="item.id">
          <el-checkbox :model-value="item.isSelected" @change="(v) => toggleSelected(item, v)" />
          <div class="r-item" @click="$router.push(`/products/${item.productId}`)">
            <el-image :src="item.mainImage || placeholder" fit="cover" class="r-img" />
            <div>
              <div class="r-name">{{ item.productName }}</div>
              <div class="r-sku">{{ item.skuName }}</div>
            </div>
          </div>
          <span class="r-price">¥{{ item.price }}</span>
          <div class="r-qty">
            <el-input-number :model-value="item.quantity" :min="1" :max="item.stock" size="small"
                             @change="(v) => changeQty(item, v)" />
          </div>
          <span class="r-sub">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
          <div class="r-op">
            <el-button link type="danger" @click="remove(item)">删除</el-button>
          </div>
        </div>

        <!-- 结算栏 -->
        <div class="cart-footer">
          <span>已选 <b class="count">{{ selectedCount }}</b> 件，合计：</span>
          <span class="total">¥{{ totalAmount.toFixed(2) }}</span>
          <el-button type="danger" size="large" :disabled="!selectedCount" @click="checkout">去结算</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCartList, updateCartQuantity, updateCartSelected, deleteCartItem } from '../api/cart'

const router = useRouter()
const items = ref([])
const placeholder = 'https://via.placeholder.com/80'

const selectedCount = computed(() => items.value.filter((i) => i.isSelected).reduce((s, i) => s + i.quantity, 0))
const totalAmount = computed(() =>
  items.value.filter((i) => i.isSelected).reduce((s, i) => s + i.price * i.quantity, 0)
)
const allSelected = computed(() => items.value.length > 0 && items.value.every((i) => i.isSelected))

async function load() {
  items.value = await getCartList()
}

function toggleAll(v) {
  items.value.forEach(async (item) => {
    item.isSelected = v
    await updateCartSelected(item.id, v)
  })
}

async function toggleSelected(item, v) {
  item.isSelected = v
  await updateCartSelected(item.id, v)
}

async function changeQty(item, v) {
  if (v === item.quantity) return
  item.quantity = v
  await updateCartQuantity(item.id, v)
}

async function remove(item) {
  await ElMessageBox.confirm('确定删除该商品吗？', '提示', { type: 'warning' })
  await deleteCartItem(item.id)
  ElMessage.success('已删除')
  load()
}

function checkout() {
  router.push('/checkout?from=cart')
}

onMounted(load)
</script>

<style scoped>
.cart-page { max-width: 1100px; margin: 0 auto; }
.cart-head, .cart-row { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f1f5f9; }
.cart-head { color: #94a3b8; font-size: 13px; }
.h-item, .r-item { flex: 1; }
.h-price, .r-price { width: 90px; text-align: right; }
.h-qty, .r-qty { width: 150px; text-align: center; }
.h-sub, .r-sub { width: 110px; text-align: right; color: #dc2626; font-weight: 600; }
.h-op, .r-op { width: 60px; text-align: center; }
.r-item { display: flex; gap: 12px; align-items: center; cursor: pointer; }
.r-img { width: 72px; height: 72px; border-radius: 8px; }
.r-name { font-weight: 600; }
.r-sku { color: #94a3b8; font-size: 12px; margin-top: 4px; }
.cart-footer { display: flex; align-items: center; justify-content: flex-end; gap: 16px; padding-top: 16px; }
.total { color: #dc2626; font-size: 22px; font-weight: 800; }
.count { color: #dc2626; }
</style>
