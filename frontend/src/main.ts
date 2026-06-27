import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router' // Router eklendi
import { useAuthStore } from './stores/authStore' // Auth store eklendi

const app = createApp(App)

app.use(createPinia())
app.use(router) // Router'ı Vue'ya bağlıyoruz

// Uygulama başlarken daha önceden kalan token varsa Axios'a ekle
const authStore = useAuthStore()
authStore.setAxiosHeader()

app.mount('#app')
