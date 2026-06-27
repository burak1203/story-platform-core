import { defineStore } from 'pinia'
import axios from 'axios'
import type { RegisterRequest, AuthenticationRequest, AuthenticationResponse } from '@/types/index'
const API_URL = 'http://localhost:8080/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || (null as string | null),
    isAuthenticated: !!localStorage.getItem('token'),
    isLoading: false,
    error: null as string | null,
  }),

  actions: {
    // Axios'a token'ı global olarak ekleyen yardımcı metot
    setAxiosHeader() {
      if (this.token) {
        axios.defaults.headers.common['Authorization'] = `Bearer ${this.token}`
      } else {
        delete axios.defaults.headers.common['Authorization']
      }
    },

    async login(credentials: any) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.post(`${API_URL}/authenticate`, credentials)
        this.token = response.data.token
        this.isAuthenticated = true
        localStorage.setItem('token', this.token!)
        this.setAxiosHeader()
      } catch (err: any) {
        this.error = 'Giriş başarısız. Bilgilerinizi kontrol edin.'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    async register(userData: any) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.post(`${API_URL}/register`, userData)
        this.token = response.data.token
        this.isAuthenticated = true
        localStorage.setItem('token', this.token!)
        this.setAxiosHeader()
      } catch (err: any) {
        this.error = 'Kayıt olunamadı. E-posta veya kullanıcı adı kullanılıyor olabilir.'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    logout() {
      this.token = null
      this.isAuthenticated = false
      localStorage.removeItem('token')
      this.setAxiosHeader()
    },
  },
})
