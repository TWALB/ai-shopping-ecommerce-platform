<template>
  <el-card>
    <template #header><b>平台数据总览</b></template>
    <el-row :gutter="16">
      <el-col :span="6" v-for="s in stats" :key="s.label">
        <el-statistic :title="s.label" :value="s.value" />
      </el-col>
    </el-row>
    <el-empty description="管理员统计接口（/api/admin/stats/**）开发完成后展示数据" style="margin-top: 30px" />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
// 后续接入：import { getStatsOverview } from '../api/admin'
const stats = ref([
  { label: '用户数', value: 0 },
  { label: '商品数', value: 0 },
  { label: '订单数', value: 0 },
  { label: '交易总额(¥)', value: 0 }
])
</script>
