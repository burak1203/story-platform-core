<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')

const handleLogin = async () => {
  if (!username.value || !password.value) return

  try {
    await authStore.login({ username: username.value, password: password.value })
    router.push('/dashboard') // Giriş başarılıysa ana panele geç
  } catch (error) {
    // Hata yönetimi authStore içinde yapılıyor, ekrana yansıyacak
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-900 text-gray-200">
    <div class="bg-slate-800 p-8 rounded-xl shadow-2xl w-full max-w-md border border-slate-700">
      <h1 class="text-3xl font-bold text-amber-500 mb-6 text-center">Yazar Stüdyosu</h1>

      <div
        v-if="authStore.error"
        class="bg-red-900/50 border border-red-500 text-red-200 p-3 mb-6 rounded text-sm"
      >
        {{ authStore.error }}
      </div>

      <form @submit.prevent="handleLogin" class="flex flex-col gap-5">
        <div>
          <label class="block text-sm font-medium text-slate-400 mb-1">Kullanıcı Adı</label>
          <input
            v-model="username"
            type="text"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-gray-100 focus:outline-none focus:border-amber-500 transition-colors"
            required
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-slate-400 mb-1">Şifre</label>
          <input
            v-model="password"
            type="password"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-gray-100 focus:outline-none focus:border-amber-500 transition-colors"
            required
          />
        </div>

        <button
          type="submit"
          class="w-full bg-amber-600 hover:bg-amber-500 text-slate-900 font-bold py-3 rounded-lg transition-colors mt-2"
          :disabled="authStore.isLoading"
        >
          {{ authStore.isLoading ? 'Giriş Yapılıyor...' : 'Giriş Yap' }}
        </button>
      </form>

      <div class="mt-6 text-center text-sm text-slate-400">
        Hesabın yok mu?
        <RouterLink to="/register" class="text-amber-500 hover:underline">Kayıt Ol</RouterLink>
      </div>
    </div>
  </div>
</template>
