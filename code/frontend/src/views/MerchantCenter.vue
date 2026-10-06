<template>
  <div class="merchant-page">
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 店铺信息 -->
        <el-tab-pane label="店铺信息" name="shop">
          <el-descriptions :column="2" border v-if="shop">
            <el-descriptions-item label="店铺名称">{{ shop.shopName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="shop.status === 1 ? 'success' : 'danger'">{{ shop.status === 1 ? '正常' : '冻结' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="店铺Logo">
              <el-image v-if="shop.shopLogo" :src="shop.shopLogo" fit="cover" class="shop-img" :preview-src-list="[shop.shopLogo]" preview-teleported />
              <span v-else class="empty-text">未设置</span>
            </el-descriptions-item>
            <el-descriptions-item label="店铺横幅">
              <el-image v-if="shop.shopBanner" :src="shop.shopBanner" fit="cover" class="shop-img" :preview-src-list="[shop.shopBanner]" preview-teleported />
              <span v-else class="empty-text">未设置</span>
            </el-descriptions-item>
            <el-descriptions-item label="店铺简介" :span="2">{{ shop.shopIntro || '暂无简介' }}</el-descriptions-item>
            <el-descriptions-item label="开店时间" :span="2">{{ shop.createdTime?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">编辑店铺</el-divider>
          <el-form :model="shopForm" label-width="100px" style="max-width: 560px">
            <el-form-item label="店铺名称">
              <el-input v-model="shopForm.shopName" maxlength="50" />
            </el-form-item>
            <el-form-item label="店铺Logo URL">
              <el-input v-model="shopForm.shopLogo" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="店铺横幅 URL">
              <el-input v-model="shopForm.shopBanner" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="店铺简介">
              <el-input v-model="shopForm.shopIntro" type="textarea" :rows="3" maxlength="500" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingShop" @click="saveShop">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 商品管理 -->
        <el-tab-pane label="商品管理" name="product">
          <div class="tool-row">
            <el-radio-group v-model="productStatus" @change="loadProducts">
              <el-radio-button :value="''">全部</el-radio-button>
              <el-radio-button :value="0">草稿</el-radio-button>
              <el-radio-button :value="1">已上架</el-radio-button>
              <el-radio-button :value="2">已下架</el-radio-button>
            </el-radio-group>
            <el-button type="primary" class="right" @click="openProductDialog()">+ 发布商品</el-button>
          </div>

          <el-table :data="products" v-loading="productLoading" stripe>
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="prod-cell">
                  <el-image :src="row.mainImage || placeholder" fit="cover" class="prod-img" />
                  <span>{{ row.productName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="price" label="售价" width="90">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" />
            <el-table-column prop="sales" label="销量" width="80" />
            <el-table-column prop="skuCount" label="SKU数" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'warning'">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openProductDialog(row)">编辑</el-button>
                <el-button v-if="row.status !== 1" size="small" type="success" @click="toggleStatus(row, 1)">上架</el-button>
                <el-button v-if="row.status === 1" size="small" type="warning" @click="toggleStatus(row, 2)">下架</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-if="productTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="productTotal" :page-size="productPageSize" v-model:current-page="productPageNum"
                         @current-change="loadProducts" />
        </el-tab-pane>

        <!-- 订单发货 -->
        <el-tab-pane label="订单发货" name="order">
          <div class="tool-row">
            <el-radio-group v-model="orderStatus" @change="loadOrders">
              <el-radio-button :value="''">全部</el-radio-button>
              <el-radio-button :value="0">待付款</el-radio-button>
              <el-radio-button :value="1">待发货</el-radio-button>
              <el-radio-button :value="2">待收货</el-radio-button>
              <el-radio-button :value="3">已完成</el-radio-button>
              <el-radio-button :value="4">已取消</el-radio-button>
            </el-radio-group>
          </div>

          <el-table :data="orders" v-loading="orderLoading" stripe>
            <el-table-column label="订单" min-width="180">
              <template #default="{ row }">
                <div class="order-cell">
                  <el-image :src="row.firstImage || placeholder" fit="cover" class="order-img" />
                  <div>
                    <div class="order-no">{{ row.orderNo }}</div>
                    <div class="order-time">{{ row.createdTime?.replace('T', ' ') }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="itemCount" label="件数" width="70" />
            <el-table-column prop="payAmount" label="实付" width="100">
              <template #default="{ row }">¥{{ row.payAmount }}</template>
            </el-table-column>
            <el-table-column label="收货人" min-width="150">
              <template #default="{ row }">
                <div>{{ row.receiverName }} {{ row.receiverPhone }}</div>
                <div class="order-addr">{{ row.receiverAddress }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="orderStatusTag(row.status)">{{ orderStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" type="primary" size="small" @click="openDeliver(row)">发货</el-button>
                <span v-else class="empty-text">—</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-if="orderTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="orderTotal" :page-size="orderPageSize" v-model:current-page="orderPageNum"
                         @current-change="loadOrders" />
        </el-tab-pane>

        <!-- 经营统计 -->
        <el-tab-pane label="经营统计" name="stats">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-card class="stat-card"><el-statistic title="累计销量(件)" :value="stats.totalSales || 0" /></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><el-statistic title="累计销售额(¥)" :value="stats.totalAmount || 0" :precision="2" /></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><el-statistic title="今日销售额(¥)" :value="stats.todayAmount || 0" :precision="2" /></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><el-statistic title="待发货订单" :value="stats.pendingShipCount || 0" /></el-card>
            </el-col>
          </el-row>
          <el-card class="block" v-if="stats.topProducts?.length">
            <template #header><b>热销商品 Top5</b></template>
            <el-table :data="stats.topProducts" stripe>
              <el-table-column prop="productName" label="商品" min-width="200" />
              <el-table-column prop="sales" label="销量" width="100" />
              <el-table-column prop="amount" label="销售额(¥)" width="140">
                <template #default="{ row }">¥{{ row.amount }}</template>
              </el-table-column>
            </el-table>
          </el-card>
          <el-empty v-else description="暂无销售数据" style="margin-top: 24px" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 商品发布/编辑弹窗 -->
    <el-dialog v-model="productDialog" :title="editingId ? '编辑商品' : '发布商品'" width="780px" top="4vh">
      <el-form :model="productForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="商品名称" required>
              <el-input v-model="productForm.productName" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-cascader v-model="productForm.categoryId" :options="categoryOptions" :props="cascaderProps"
                           clearable placeholder="选择分类" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="卖点标题">
              <el-input v-model="productForm.productTitle" maxlength="300" placeholder="商品卖点/副标题（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="productForm.brandId" clearable placeholder="选择品牌" style="width: 100%">
                <el-option v-for="b in brandOptions" :key="b.id" :value="b.id" :label="b.brandName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="主图URL">
          <el-input v-model="productForm.mainImage" placeholder="https://..." />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="售价" required>
              <el-input-number v-model="productForm.price" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="原价">
              <el-input-number v-model="productForm.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总库存" required>
              <el-input-number v-model="productForm.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关键词/标签">
          <el-input v-model="productForm.keywords" placeholder="逗号分隔（选填）" />
          <el-input v-model="productForm.tags" placeholder="标签，逗号分隔（选填）" class="mt8" />
        </el-form-item>
        <el-form-item label="商品简介">
          <el-input v-model="productForm.brief" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>

        <!-- SKU -->
        <el-form-item label="规格SKU">
          <div class="line-box">
            <div v-for="(sku, i) in productForm.skus" :key="i" class="line-row">
              <el-input v-model="sku.skuName" placeholder="规格名（如 曜石黑/256G）" class="line-w1" />
              <el-input-number v-model="sku.price" :min="0" :precision="2" placeholder="价格" class="line-w2" />
              <el-input-number v-model="sku.stock" :min="0" placeholder="库存" class="line-w2" />
              <el-input v-model="sku.skuImage" placeholder="图片URL（选填）" class="line-w3" />
              <el-button type="danger" link @click="productForm.skus.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="productForm.skus.push({ skuName: '', price: null, stock: 0, skuImage: '' })">+ 添加规格</el-button>
          </div>
        </el-form-item>

        <!-- 参数 -->
        <el-form-item label="商品参数">
          <div class="line-box">
            <div v-for="(attr, i) in productForm.attrs" :key="i" class="line-row">
              <el-input v-model="attr.attrName" placeholder="参数名（如 屏幕）" class="line-w1" />
              <el-input v-model="attr.attrValue" placeholder="参数值（如 6.7英寸）" class="line-w3" />
              <el-button type="danger" link @click="productForm.attrs.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="productForm.attrs.push({ attrName: '', attrValue: '' })">+ 添加参数</el-button>
          </div>
        </el-form-item>

        <!-- 图片 -->
        <el-form-item label="轮播/详情图">
          <div class="line-box">
            <div v-for="(img, i) in productForm.images" :key="i" class="line-row">
              <el-input v-model="img.imageUrl" placeholder="图片URL" class="line-w3" />
              <el-select v-model="img.imageType" class="line-w2">
                <el-option :value="0" label="轮播图" />
                <el-option :value="1" label="详情图" />
              </el-select>
              <el-button type="danger" link @click="productForm.images.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="productForm.images.push({ imageUrl: '', imageType: 0 })">+ 添加图片</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingProduct" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发货弹窗 -->
    <el-dialog v-model="deliverDialog" title="订单发货" width="460px">
      <el-form :model="deliverForm" label-width="90px">
        <el-form-item label="物流公司" required>
          <el-input v-model="deliverForm.companyName" placeholder="如 顺丰速运" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="deliverForm.logisticsNo" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverDialog = false">取消</el-button>
        <el-button type="primary" :loading="delivering" @click="doDeliver">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMyShop, updateMyShop, getMerchantProductPage, addMerchantProduct, updateMerchantProduct,
  updateProductStatus, getMerchantOrderPage, deliverOrder, getMerchantStats
} from '../api/merchant'
import { getCategoryTree, getBrandList, getProductDetail } from '../api/product'

const placeholder = 'https://via.placeholder.com/80'
const activeTab = ref('shop')

// ===== 店铺 =====
const shop = ref(null)
const shopForm = ref({ shopName: '', shopLogo: '', shopBanner: '', shopIntro: '' })
const savingShop = ref(false)

async function loadShop() {
  shop.value = await getMyShop()
  shopForm.value = {
    shopName: shop.value.shopName || '',
    shopLogo: shop.value.shopLogo || '',
    shopBanner: shop.value.shopBanner || '',
    shopIntro: shop.value.shopIntro || ''
  }
}

async function saveShop() {
  savingShop.value = true
  try {
    await updateMyShop(shopForm.value)
    ElMessage.success('店铺信息已保存')
    loadShop()
  } catch (e) { /* 忽略 */ } finally {
    savingShop.value = false
  }
}

// ===== 商品 =====
const products = ref([])
const productTotal = ref(0)
const productPageNum = ref(1)
const productPageSize = 10
const productStatus = ref('')
const productLoading = ref(false)
const productDialog = ref(false)
const editingId = ref(null)
const savingProduct = ref(false)
const categoryOptions = ref([])
const brandOptions = ref([])
const cascaderProps = { value: 'id', label: 'categoryName', children: 'children', checkStrictly: true, emitPath: false }
const productForm = ref(emptyProductForm())

const statusMap = { 0: '草稿', 1: '已上架', 2: '已下架' }
const statusText = (s) => statusMap[s] || '未知'

function emptyProductForm() {
  return {
    categoryId: null, brandId: null, productName: '', productTitle: '', mainImage: '',
    keywords: '', tags: '', brief: '', price: null, originalPrice: null, stock: 0,
    skus: [], attrs: [], images: []
  }
}

async function loadProducts() {
  productLoading.value = true
  try {
    const data = await getMerchantProductPage({
      pageNum: productPageNum.value,
      pageSize: productPageSize,
      status: productStatus.value === '' ? undefined : productStatus.value
    })
    products.value = data.list || []
    productTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    productLoading.value = false
  }
}

async function openProductDialog(row) {
  productForm.value = emptyProductForm()
  if (row) {
    editingId.value = row.id
    const detail = await getProductDetail(row.id)
    productForm.value = {
      categoryId: detail.product.categoryId,
      brandId: detail.product.brandId,
      productName: detail.product.productName,
      productTitle: detail.product.productTitle || '',
      mainImage: detail.product.mainImage || '',
      keywords: detail.product.keywords || '',
      tags: detail.product.tags || '',
      brief: detail.product.brief || '',
      price: Number(detail.product.price),
      originalPrice: detail.product.originalPrice ? Number(detail.product.originalPrice) : null,
      stock: detail.product.stock,
      skus: (detail.skus || []).map(s => ({ skuName: s.skuName, price: Number(s.price), stock: s.stock, skuImage: s.skuImage || '' })),
      attrs: (detail.attrs || []).map(a => ({ attrName: a.attrName, attrValue: a.attrValue })),
      images: (detail.images || []).map(i => ({ imageUrl: i.imageUrl, imageType: i.imageType }))
    }
  } else {
    editingId.value = null
  }
  productDialog.value = true
}

async function saveProduct() {
  if (!productForm.value.productName || !productForm.value.price) {
    ElMessage.warning('请填写商品名称与售价')
    return
  }
  savingProduct.value = true
  try {
    if (editingId.value) {
      await updateMerchantProduct(editingId.value, productForm.value)
      ElMessage.success('商品已更新')
    } else {
      await addMerchantProduct(productForm.value)
      ElMessage.success('商品已发布（草稿），可上架展示')
    }
    productDialog.value = false
    loadProducts()
  } catch (e) { /* 忽略 */ } finally {
    savingProduct.value = false
  }
}

async function toggleStatus(row, status) {
  const action = status === 1 ? '上架' : '下架'
  await ElMessageBox.confirm(`确定${action}商品「${row.productName}」吗？`, '提示', { type: 'warning' })
  await updateProductStatus(row.id, status)
  ElMessage.success(`已${action}`)
  loadProducts()
}

// ===== 订单 =====
const orders = ref([])
const orderTotal = ref(0)
const orderPageNum = ref(1)
const orderPageSize = 10
const orderStatus = ref('')
const orderLoading = ref(false)
const deliverDialog = ref(false)
const delivering = ref(false)
const deliverForm = ref({ orderId: null, companyName: '', logisticsNo: '' })

const orderStatusMap = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const orderStatusText = (s) => orderStatusMap[s] || '未知'
const orderStatusTag = (s) => (s === 0 ? 'warning' : s === 1 ? 'danger' : s === 2 ? 'primary' : 'info')

async function loadOrders() {
  orderLoading.value = true
  try {
    const data = await getMerchantOrderPage({
      pageNum: orderPageNum.value,
      pageSize: orderPageSize,
      status: orderStatus.value === '' ? undefined : orderStatus.value
    })
    orders.value = data.list || []
    orderTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    orderLoading.value = false
  }
}

function openDeliver(row) {
  deliverForm.value = { orderId: row.id, companyName: '', logisticsNo: '' }
  deliverDialog.value = true
}

async function doDeliver() {
  if (!deliverForm.value.companyName || !deliverForm.value.logisticsNo) {
    ElMessage.warning('请填写物流公司与单号')
    return
  }
  delivering.value = true
  try {
    await deliverOrder(deliverForm.value.orderId, deliverForm.value.logisticsNo, deliverForm.value.companyName)
    ElMessage.success('发货成功，订单已进入待收货')
    deliverDialog.value = false
    loadOrders()
  } catch (e) { /* 忽略 */ } finally {
    delivering.value = false
  }
}

// ===== 统计 =====
const stats = ref({})

async function loadStats() {
  stats.value = await getMerchantStats()
}

// ===== 初始化 =====
async function loadMeta() {
  try {
    const cats = await getCategoryTree()
    categoryOptions.value = cats || []
    brandOptions.value = await getBrandList() || []
  } catch (e) { /* 忽略 */ }
}

onMounted(async () => {
  await loadShop()
  loadMeta()
  if (activeTab.value === 'product') loadProducts()
})
</script>

<style scoped>
.merchant-page { max-width: 1100px; margin: 0 auto; }
.shop-img { width: 120px; height: 80px; border-radius: 6px; }
.empty-text { color: #94a3b8; font-size: 13px; }
.tool-row { display: flex; align-items: center; margin-bottom: 14px; }
.tool-row .right { margin-left: auto; }
.prod-cell { display: flex; align-items: center; gap: 10px; }
.prod-img { width: 52px; height: 52px; border-radius: 6px; }
.order-cell { display: flex; align-items: center; gap: 10px; }
.order-img { width: 52px; height: 52px; border-radius: 6px; }
.order-no { font-weight: 600; }
.order-time, .order-addr { color: #94a3b8; font-size: 12px; margin-top: 2px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.stat-card { text-align: center; }
.block { margin-top: 16px; }
.mt8 { margin-top: 8px; }
.line-box { width: 100%; }
.line-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-w1 { width: 200px; }
.line-w2 { width: 130px; }
.line-w3 { flex: 1; }
</style>
