<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo">秒杀系统</div>
      <el-menu mode="horizontal" :ellipsis="false" router background-color="#1a1a2e" text-color="#eee" active-text-color="#e94560">
        <el-menu-item index="/products">商品</el-menu-item>
        <el-menu-item index="/orders" v-if="isLogin">我的订单</el-menu-item>
        <div class="flex-grow" />
        <el-menu-item index="/login" v-if="!isLogin">登录</el-menu-item>
        <el-menu-item index="/login" v-else @click="logout">退出 ({{ username }})</el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { AUTH_CHANGED, notifyAuthChanged, readAuthFromStorage } from './composables/auth'

const router = useRouter()
const isLogin = ref(!!readAuthFromStorage().token)
const username = ref(readAuthFromStorage().username)

function refreshAuth() {
  const auth = readAuthFromStorage()
  isLogin.value = !!auth.token
  username.value = auth.username
}

function logout() {
  localStorage.clear()
  notifyAuthChanged()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener(AUTH_CHANGED, refreshAuth)
})

onUnmounted(() => {
  window.removeEventListener(AUTH_CHANGED, refreshAuth)
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'Segoe UI', sans-serif; background: #f5f6fa; }
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; background: #1a1a2e; padding: 0 20px; }
.logo { color: #e94560; font-size: 22px; font-weight: bold; margin-right: 40px; }
.flex-grow { flex-grow: 1; }
.el-header { height: 60px !important; }
</style>
