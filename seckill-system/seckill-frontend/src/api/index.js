import axios from 'axios'
import { ElMessage } from 'element-plus'
import { notifyAuthChanged } from '../composables/auth'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => {
    if (res.data.code !== 200) {
      ElMessage.error(res.data.message || '请求失败')
      return Promise.reject(res.data)
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.clear()
      notifyAuthChanged()
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login'
      }
    }
    if (!err.config?.silent) {
      let msg = err.response?.data?.message || err.message
      if (err.response?.status === 429 && err.config?.url?.includes('/seckill/') && err.config?.method === 'post') {
        msg = String(msg).includes('重复') ? '您已参与过该商品秒杀' : '操作过于频繁，请稍后再试'
      }
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data)
}

export const productApi = {
  list: () => api.get('/products'),
  get: (id) => api.get(`/products/${id}`)
}

export const seckillApi = {
  buy: (productId) => api.post(`/seckill/${productId}`),
  result: (productId) => api.get(`/seckill/result/${productId}`, { silent: true })
}

export const orderApi = {
  list: () => api.get('/orders')
}

export default api
