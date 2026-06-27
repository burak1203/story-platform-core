<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const storyStore = useStoryStore()
const authStore = useAuthStore()

const isCreating = ref(false)
const newTitle = ref('')
const newPrompt = ref('')

onMounted(() => {
  storyStore.fetchMyStories()
})

const handleCreateStory = async () => {
  if (!newTitle.value || !newPrompt.value) return

  try {
    const newStory = await storyStore.createStory({
      title: newTitle.value,
      startingPrompt: newPrompt.value,
    })
    // Hikaye veritabanında oluştu (PENDING), doğrudan stüdyoya yönlendir
    router.push(`/studio/${newStory.id}`)
  } catch (error) {
    // Hata ekranda gösterilecek
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

const goToStudio = (id: number) => {
  router.push(`/studio/${id}`)
}

const handleDelete = async (id: number) => {
  if (confirm('Bu hikayeyi silmek istediğinize emin misiniz?')) {
    await storyStore.deleteStory(id)
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-900 text-gray-200 font-sans p-8">
    <header class="flex justify-between items-center mb-12 border-b border-slate-700 pb-6">
      <h1 class="text-3xl font-bold text-amber-500">Hikayelerim</h1>
      <button
        @click="handleLogout"
        class="text-sm bg-slate-800 hover:bg-slate-700 px-4 py-2 rounded border border-slate-600 transition-colors"
      >
        Çıkış Yap
      </button>
    </header>

    <div class="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
      <div
        class="bg-slate-800/50 border-2 border-dashed border-slate-600 rounded-xl p-6 flex flex-col justify-center transition-colors"
        :class="{ 'hover:border-amber-500': !isCreating }"
      >
        <div v-if="!isCreating" @click="isCreating = true" class="cursor-pointer text-center py-8">
          <div class="text-4xl text-slate-500 mb-2">+</div>
          <div class="font-bold text-slate-400">Yeni Serüven Başlat</div>
        </div>

        <form v-else @submit.prevent="handleCreateStory" class="flex flex-col gap-4">
          <h2 class="text-lg font-bold text-amber-500 mb-2">Başlangıç Kurulumu</h2>

          <input
            v-model="newTitle"
            type="text"
            placeholder="Hikaye Başlığı (Örn: Mars Kolonisi)"
            class="bg-slate-900 border border-slate-700 rounded px-4 py-2 text-sm focus:outline-none focus:border-amber-500"
            required
            :disabled="storyStore.isLoading"
          />

          <textarea
            v-model="newPrompt"
            rows="4"
            placeholder="Nasıl başlıyoruz? (Örn: Gözlerimi açtığımda kırmızı kumlar fırtınayla savruluyordu...)"
            class="bg-slate-900 border border-slate-700 rounded px-4 py-2 text-sm focus:outline-none focus:border-amber-500 resize-none"
            required
            :disabled="storyStore.isLoading"
          ></textarea>

          <div class="flex gap-2 mt-2">
            <button
              type="submit"
              class="flex-1 bg-amber-600 hover:bg-amber-500 text-slate-900 font-bold py-2 rounded transition-colors"
              :disabled="storyStore.isLoading"
            >
              {{ storyStore.isLoading ? 'Kuruluyor...' : 'Başlat' }}
            </button>
            <button
              type="button"
              @click="isCreating = false"
              class="bg-slate-700 hover:bg-slate-600 px-4 rounded transition-colors"
              :disabled="storyStore.isLoading"
            >
              İptal
            </button>
          </div>

          <div v-if="storyStore.error" class="text-red-400 text-xs mt-2">
            {{ storyStore.error }}
          </div>
        </form>
      </div>

      <div
        v-for="story in storyStore.myStories"
        :key="story.id"
        @click="goToStudio(story.id)"
        class="bg-slate-800 border border-slate-700 rounded-xl p-6 cursor-pointer hover:bg-slate-750 hover:border-slate-500 transition-all flex flex-col h-64"
      >
        <div class="flex justify-between items-start mb-4">
          <h3 class="text-xl font-bold text-slate-100 truncate pr-4">{{ story.title }}</h3>

          <div class="flex gap-2 items-center">
            <span class="bg-slate-700 text-xs px-2 py-1 rounded text-slate-300"
              >Hamle: {{ story.actionCount }}</span
            >
            <button
              @click.stop="handleDelete(story.id)"
              class="text-slate-500 hover:text-red-500 transition-colors"
              title="Hikayeyi Sil"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                />
              </svg>
            </button>
          </div>
        </div>

        <div class="flex-1 text-sm text-slate-400 overflow-hidden relative">
          <div class="absolute inset-0 bg-gradient-to-b from-transparent to-slate-800"></div>
          {{ story.currentSummary || story.content || 'Henüz içerik yok...' }}
        </div>

        <div
          class="mt-4 pt-4 border-t border-slate-700 flex justify-between text-xs text-slate-500"
        >
          <span
            >Durum:
            <span
              :class="{
                'text-amber-500': story.status === 'PENDING',
                'text-emerald-500': story.status === 'COMPLETED',
              }"
              >{{ story.status }}</span
            ></span
          >
          <span class="text-amber-600 hover:underline">Devam Et &rarr;</span>
        </div>
      </div>
    </div>
  </div>
</template>
