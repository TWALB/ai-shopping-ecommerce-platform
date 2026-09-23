<template>
  <div>
    <!-- 搜索与筛选栏 -->
    <el-card class="filter-card">
      <el-form inline>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="搜索商品 / 品牌 / 关键词" clearable style="width: 240px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 150px">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.brandId" placeholder="全部品牌" clearable style="width: 140px">
            <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input-number v-model="query.minPrice" :min="0" placeholder="最低价" style="width: 110px" />
          <span style="margin: 0 6px">-</span>
          <el-input-number v-model="query.maxPrice" :min="0" placeholder="最高价" style="width: 110px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.sortBy" style="width: 130px" @change="search">
            <el-option label="综合排序" value="comprehensive" />
            <el-option label="销量优先" value="sales" />
            <el-option label="价格从低到高" value="price_asc" />
            <el-option label="价格从高到低" value="price_desc" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <!-- 商品网格 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="p in products" :key="p.id">
        <el-card class="product-card" shadow="hover">
          <el-image :src="p.mainImage || placeholder" fit="cover" class="p-img" />
          <div class="p-name">{{ p.productName }}</div>
          <div class="p-meta">
            <span class="p-price">¥{{ p.price }}</span>
            <span class="p-sales">已售{{ p.sales }}</span>
          </div>
          <el-button type="primary" size="small" style="width: 100%; margin-top: 8px"
                     @click="$router.push(`/products/${p.id}`)">查看详情</el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!products.length" description="暂无商品（后端商品接口开发完成后展示）" />

    <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next"
                   :total="total" :page-size="query.pageSize" v-model:current-page="query.pageNum"
                   @current-change="search" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getProductPage, getCategoryTree, getBrandList } from '../api/product'

const products = ref([])
const categories = ref([])
const brands = ref([])
const total = ref(0)
const placeholder = 'https://via.placeholder.com/300'
const query = ref({ keyword: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null, sortBy: 'comprehensive', pageNum: 1, pageSize: 12 })

async function search() {
  try {
    const params = {
      keyword: query.value.keyword || undefined,
      categoryId: query.value.categoryId || undefined,
      brandId: query.value.brandId || undefined,
      minPrice: query.value.minPrice ?? undefined,
      maxPrice: query.value.maxPrice ?? undefined,
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize
    }
    // 排序映射：综合/销量/价格
    if (query.value.sortBy === 'price_asc') { params.sortBy = 'price'; params.order = 'asc' }
    else if (query.value.sortBy === 'price_desc') { params.sortBy = 'price'; params.order = 'desc' }
    else if (query.value.sortBy === 'sales') { params.sortBy = 'sales'; params.order = 'desc' }
    const data = await getProductPage(params)
    products.value = data.list || []
    total.value = data.total || 0
  } catch (e) { /* 后端未实现时保持空 */ }
}

onMounted(async () => {
  search()
  try {
    categories.value = await getCategoryTree()
    brands.value = await getBrandList()
  } catch (e) { /* 忽略 */ }
})
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.product-card { margin-bottom: 16px; cursor: pointer; }
.p-img { width: 100%; height: 160px; }
.p-name { font-weight: 600; margin: 8px 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.p-meta { display: flex; justify-content: space-between; align-items: center; }
.p-price { color: #dc2626; font-weight: 700; }
.p-sales { color: #94a3b8; font-size: 12px; }
.pager { margin-top: 16px; justify-content: center; }
</style>
