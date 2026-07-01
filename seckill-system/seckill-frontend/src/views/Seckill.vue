<template>
  <div class="seckill-page" v-if="product">
    <el-card class="detail-card">
      <h2>{{ product.name }}</h2>
      <p class="price">¥{{ product.price }}</p>
      <p class="stock">
        剩余库存: <el-tag :type="product.remainStock > 0 ? 'success' : 'danger'" size="large">
          {{ product.remainStock }} / {{ product.seckillStock }}
        </el-tag>
      </p>
      <el-button type="danger" size="large" :loading="!initialized || loading"
                 :disabled="!initialized || product.remainStock <= 0 || polling || participated || loading"
                 @click="doSeckill">
        {{ buttonText }}
      </el-button>
      <div v-if="result" class="result">
        <el-alert :title="result.message" :type="resultType" show-icon :closable="false" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { productApi, seckillApi } from '../api'

const route = useRoute()
const productId = route.params.id
const product = ref(null)
const loading = ref(false)
const polling = ref(false)
const participated = ref(false)
const initialized = ref(false)
const result = ref(null)
let stopped = false

const resultType = computed(() => {
  if (!result.value) return 'info'
  if (result.value.status === 'SUCCESS') return 'success'
  if (result.value.status === 'PENDING') return 'warning'
  const msg = result.value.message || ''
  if (msg.includes('处理较慢') || msg.includes('我的订单')) return 'warning'
  return 'error'
})

const buttonText = computed(() => {
  if (!initialized.value) return '加载中...'
  if (participated.value) return '已参与秒杀'
  if (product.value?.remainStock <= 0) return '已售罄'
  return '立即秒杀'
})

function markParticipatedIfNeeded(data) {
  if (data.status !== 'NONE') {
    participated.value = true
  }
}

async function syncFromExistingResult() {
  participated.value = true
  try {
    const r = await seckillApi.result(productId)
    if (r.data.status !== 'NONE') {
      result.value = r.data
      markParticipatedIfNeeded(r.data)
    }
  } catch {
    // ignore
  }
}

onMounted(async () => {
  try {
    const res = await productApi.get(productId)
    product.value = res.data
    try {
      const resultRes = await seckillApi.result(productId)
      if (resultRes.data.status !== 'NONE') {
        result.value = resultRes.data
        markParticipatedIfNeeded(resultRes.data)
        if (resultRes.data.status === 'PENDING') {
          polling.value = true
          pollResult(productId)
        }
      }
    } catch {
      // 未登录或查询失败时忽略
    }
  } finally {
    initialized.value = true
  }
})

onUnmounted(() => {
  stopped = true
  polling.value = false
})

async function doSeckill() {
  if (!initialized.value || loading.value || participated.value) return
  loading.value = true
  try {
    const res = await seckillApi.buy(productId)
    result.value = res.data
    markParticipatedIfNeeded(res.data)
    if (res.data.status === 'PENDING') {
      polling.value = true
      pollResult(productId)
    }
  } catch (err) {
    if (err.response?.status === 429) {
      await syncFromExistingResult()
    }
  } finally {
    loading.value = false
  }
}

async function pollResult(id) {
  for (let i = 0; i < 20; i++) {
    if (stopped) return
    await new Promise(r => setTimeout(r, 1000))
    if (stopped) return
    try {
      const res = await seckillApi.result(id)
      if (stopped) return
      result.value = res.data
      if (res.data.status !== 'PENDING') {
        polling.value = false
        markParticipatedIfNeeded(res.data)
        const p = await productApi.get(id)
        if (!stopped) product.value = p.data
        return
      }
    } catch {
      // 轮询失败时静默重试
    }
  }
  polling.value = false
  if (!stopped && result.value?.status === 'PENDING') {
    participated.value = true
    result.value = {
      status: 'FAILED',
      message: '处理较慢，请到「我的订单」查看状态'
    }
  }
}
</script>

<style scoped>
.seckill-page { max-width: 600px; margin: 0 auto; }
.detail-card { text-align: center; padding: 40px; }
.price { font-size: 36px; color: #e94560; margin: 16px 0; }
.stock { margin: 20px 0; }
.result { margin-top: 24px; }
</style>
