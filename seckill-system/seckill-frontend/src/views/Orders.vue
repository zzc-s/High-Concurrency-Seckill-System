<template>
  <div class="orders-page">
    <h2>我的订单</h2>
    <el-table :data="orders" stripe>
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="productId" label="商品ID" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { orderApi } from '../api'

const orders = ref([])
let refreshTimer = null

const statusText = (s) => ({ 0: '处理中', 1: '成功', 2: '失败' }[s] || '未知')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info')

async function loadOrders() {
  const res = await orderApi.list()
  orders.value = res.data
  return res.data
}

function startRefreshIfNeeded(data) {
  if (refreshTimer) return
  if (data.some(o => o.status === 0)) {
    refreshTimer = setInterval(async () => {
      const list = await loadOrders()
      if (!list.some(o => o.status === 0)) {
        clearInterval(refreshTimer)
        refreshTimer = null
      }
    }, 3000)
  }
}

onMounted(async () => {
  const data = await loadOrders()
  startRefreshIfNeeded(data)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.orders-page { max-width: 900px; margin: 0 auto; }
h2 { margin-bottom: 24px; color: #1a1a2e; }
</style>
