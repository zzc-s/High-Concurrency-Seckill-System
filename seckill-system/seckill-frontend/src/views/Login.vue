<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>{{ isRegister ? '注册' : '登录' }}</h2>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">
          {{ isRegister ? '注册' : '登录' }}
        </el-button>
      </el-form>
      <p class="toggle" @click="isRegister = !isRegister">
        {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { notifyAuthChanged } from '../composables/auth'

const router = useRouter()
const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  loading.value = true
  try {
    const fn = isRegister.value ? authApi.register : authApi.login
    const res = await fn(form)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userId', res.data.userId)
    localStorage.setItem('username', res.data.username)
    notifyAuthChanged()
    ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
    router.push('/products')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { display: flex; justify-content: center; align-items: center; min-height: 70vh; }
.login-card { width: 400px; padding: 20px; }
h2 { text-align: center; margin-bottom: 24px; color: #1a1a2e; }
.toggle { text-align: center; margin-top: 16px; color: #e94560; cursor: pointer; }
</style>
