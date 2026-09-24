<template>
  <div v-if="detail" class="detail-page">
    <el-row :gutter="24">
      <!-- 左：图片区 -->
      <el-col :span="10">
        <el-image :src="detail.product.mainImage || placeholder" fit="cover" class="main-img" />
        <div class="thumb-row">
          <el-image v-for="img in detail.images" :key="img.id" :src="img.imageUrl || placeholder"
                    fit="cover" class="thumb" />
        </div>
      </el-col>

      <!-- 右：信息区 -->
      <el-col :span="14">
        <h1 class="p-title">{{ detail.product.productName }}</h1>
        <p class="p-brief">{{ detail.product.productTitle || detail.product.brief }}</p>
        <div class="price-box">
          <span class="price">¥{{ detail.product.price }}</span>
          <span class="origin-price" v-if="detail.product.originalPrice">¥{{ detail.product.originalPrice }}</span>
          <el-tag size="small" type="warning" v-if="detail.product.tags">{{ detail.product.tags }}</el-tag>
        </div>
        <div class="meta-row">
          <span>已售 {{ detail.product.sales }} 件</span>
          <span>浏览 {{ detail.product.viewCount }}</span>
          <span>库存 {{ detail.product.stock }}</span>
        </div>

        <!-- 店铺信息 -->
        <div class="shop-box" v-if="detail.shop">
          <el-icon><Shop /></el-icon>
          <span class="shop-name">{{ detail.shop.shopName }}</span>
        </div>

        <!-- 规格选择 -->
        <div v-if="detail.skus.length" class="sku-box">
          <div class="sku-label">选择规格：</div>
          <el-radio-group v-model="selectedSkuId">
            <el-radio-button v-for="s in detail.skus" :key="s.id" :value="s.id">
              {{ s.skuName }}（¥{{ s.price }}）
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 数量 -->
        <div class="qty-box">
          <span class="sku-label">数量：</span>
          <el-input-number v-model="quantity" :min="1" :max="maxStock" />
        </div>

        <div class="action-row">
          <el-button type="primary" size="large" @click="addCart">加入购物车</el-button>
          <el-button type="danger" size="large" @click="buyNow">立即购买</el-button>
          <el-button size="large" @click="toggleFavorite">
            {{ isFavorite ? '取消收藏' : '收藏' }}
          </el-button>
        </div>
      </el-col>
    </el-row>

    <!-- 参数 -->
    <el-card v-if="detail.attrs.length" class="block">
      <template #header><b>商品参数</b></template>
      <table class="attr-table">
        <tr v-for="a in detail.attrs" :key="a.id">
          <td class="attr-name">{{ a.attrName }}</td>
          <td>{{ a.attrValue }}</td>
        </tr>
      </table>
    </el-card>

    <!-- 评价 -->
    <el-card class="block">
      <template #header>
        <b>商品评价（{{ detail.commentSummary.total }}）</b>
        <span class="score">商品均分 {{ detail.commentSummary.avgProductScore?.toFixed(1) }}</span>
      </template>
      <el-empty description="暂无评价（评价模块开发后展示）" />
    </el-card>

    <el-button class="back-btn" @click="$router.back()">← 返回</el-button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { addToCart } from '../api/cart'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const selectedSkuId = ref(null)
const quantity = ref(1)
const isFavorite = ref(false)
const placeholder = 'https://via.placeholder.com/400'

const maxStock = computed(() => {
  if (!detail.value) return 1
  if (selectedSkuId.value) {
    const sku = detail.value.skus.find((s) => s.id === selectedSkuId.value)
    return sku ? Math.max(sku.stock, 1) : 1
  }
  return Math.max(detail.value.product.stock, 1)
})

async function load() {
  detail.value = await getProductDetail(route.params.id)
  if (detail.value.skus.length) selectedSkuId.value = detail.value.skus[0].id
}

async function addCart() {
  if (!selectedSkuId.value) {
    ElMessage.warning('请先选择规格')
    return
  }
  await addToCart(selectedSkuId.value, quantity.value)
  ElMessage.success('已加入购物车')
}

function buyNow() {
  if (!selectedSkuId.value) {
    ElMessage.warning('请先选择规格')
    return
  }
  const p = detail.value.product
  router.push(`/checkout?from=buy&productId=${p.id}&skuId=${selectedSkuId.value}&quantity=${quantity.value}`)
}

function toggleFavorite() {
  ElMessage.info('收藏功能后续迭代实现')
}

onMounted(load)
</script>

<style scoped>
.detail-page { max-width: 1200px; margin: 0 auto; }
.main-img { width: 100%; height: 380px; border-radius: 10px; }
.thumb-row { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.thumb { width: 72px; height: 72px; border-radius: 8px; }
.p-title { font-size: 24px; margin-bottom: 8px; }
.p-brief { color: #64748b; margin-bottom: 12px; }
.price-box { display: flex; align-items: center; gap: 12px; background: #fff1f2; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; }
.price { color: #dc2626; font-size: 26px; font-weight: 800; }
.origin-price { color: #94a3b8; text-decoration: line-through; }
.meta-row { display: flex; gap: 20px; color: #64748b; font-size: 13px; margin-bottom: 12px; }
.shop-box { display: flex; align-items: center; gap: 6px; padding: 10px 0; border-top: 1px dashed #e2e8f0; border-bottom: 1px dashed #e2e8f0; margin-bottom: 12px; }
.shop-name { font-weight: 600; }
.sku-box { margin-bottom: 12px; }
.sku-label { font-size: 14px; color: #475569; margin-bottom: 6px; }
.qty-box { margin-bottom: 16px; }
.action-row { display: flex; gap: 12px; flex-wrap: wrap; }
.block { margin-top: 20px; }
.score { margin-left: 12px; color: #64748b; font-size: 13px; }
.attr-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.attr-table td { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; }
.attr-name { width: 140px; color: #64748b; background: #f8fafc; }
.back-btn { margin-top: 20px; }
</style>
