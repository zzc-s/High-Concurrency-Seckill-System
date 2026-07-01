import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Products from '../views/Products.vue'
import Seckill from '../views/Seckill.vue'
import Orders from '../views/Orders.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/login', component: Login },
    { path: '/products', component: Products },
    { path: '/seckill/:id', component: Seckill, meta: { auth: true } },
    { path: '/orders', component: Orders, meta: { auth: true } }
  ]
})

router.beforeEach((to, from, next) => {
  if (to.meta.auth && !localStorage.getItem('token')) {
    next('/login')
  } else {
    next()
  }
})

export default router
