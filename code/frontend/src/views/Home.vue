<template>
  <div>
    <el-card class="banner">
      <h1>欢迎来到数码电商平台</h1>
      <p>大模型智能导购 · 帮您快速找到合适的数码产品</p>
      <el-button type="primary" size="large" @click="$router.push('/chat')">开始智能导购 →</el-button>
    </el-card>

    <h3 class="section-title">🔥 热门商品</h3>
    <el-row :gutter="16">
      <el-col :span="6" v-for="p in hotProducts" :key="p.id">
        <el-card class="product-card" shadow="hover" @click="$router.push(`/products/${p.id}`)">
          <el-image :src="p.mainImage || placeholder" fit="cover" class="p-img" />
          <div class="p-name">{{ p.productName }}</div>
          <div class="p-price">¥{{ p.price }}</div>
          <div class="p-sales">已售 {{ p.sales }} 件</div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!hotProducts.length" description="暂无热门商品（后端开发完成后展示）" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHotProducts } from '../api/product'

const hotProducts = ref([])
const placeholder = 'https://via.placeholder.com/300'

onMounted(async () => {
  try {
    hotProducts.value = await getHotProducts()
  } catch (e) { /* 后端未实现时保持空 */ }
})
</script>

<style scoped>
.banner { text-align: center; padding: 40px 0; margin-bottom: 20px; background: linear-gradient(135deg, #eff6ff, #dbeafe); }
.banner h1 { margin-bottom: 8px; color: #1e3a8a; }
.banner p { color: #64748b; margin-bottom: 16px; }
.section-title { margin: 16px 0; }
.product-card { cursor: pointer; margin-bottom: 16px; }
.p-img { width: 100%; height: 160px; }
.p-name { font-weight: 600; margin: 8px 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.p-price { color: #dc2626; font-weight: 700; }
.p-sales { color: #94a3b8; font-size: 12px; }
</style>
