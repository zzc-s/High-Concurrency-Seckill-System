<template>
  <div class="products-page">
    <h2>秒杀商品</h2>
    <el-row :gutter="20">
      <el-col :span="8" v-for="p in products" :key="p.id">
        <el-card shadow="hover" class="product-card">
          <h3>{{ p.name }}</h3>
          <p class="price">¥{{ p.price }}</p>
          <p class="stock">剩余库存: <strong>{{ p.remainStock }}</strong> / {{ p.seckillStock }}</p>
          <el-button type="danger" @click="$router.push(`/seckill/${p.id}`)">立即秒杀</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { productApi } from '../api'

const products = ref([])

onMounted(async () => {
  const res = await productApi.list()
  products.value = res.data
})
</script>

<style scoped>
.products-page { max-width: 1200px; margin: 0 auto; }
h2 { margin-bottom: 24px; color: #1a1a2e; }
.product-card { margin-bottom: 20px; text-align: center; }
.price { font-size: 28px; color: #e94560; margin: 12px 0; }
.stock { color: #666; margin-bottom: 16px; }
</style>
