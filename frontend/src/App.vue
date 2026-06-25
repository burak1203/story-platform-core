<script setup lang="ts">
import { ref } from 'vue'
import { useStoryStore } from './stores/storyStore'

const store = useStoryStore()
const userInput = ref('')

// Enter tuşuna veya butona basıldığında çalışacak fonksiyon
const handleAction = async () => {
  if (!userInput.value.trim() || store.isLoading) return

  const input = userInput.value
  userInput.value = '' // İstek atılırken inputu hemen temizle

  if (!store.storyId) {
    // Hikaye ID'si yoksa, bu girilen ilk prompt'tur
    await store.startNewStory(input)
  } else {
    // Hikaye zaten varsa, yeni hamle olarak devam ettir
    await store.continueStory(input)
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-900 text-gray-100 flex overflow-hidden font-sans">
    <aside class="w-1/4 bg-gray-800 border-r border-gray-700 flex flex-col">
      <div class="p-4 border-b border-gray-700">
        <h2 class="text-sm font-bold text-gray-400 uppercase tracking-wider">Evren Hafızası</h2>
      </div>
      <div
        class="p-4 flex-1 overflow-y-auto text-sm text-gray-300 leading-relaxed whitespace-pre-wrap"
      >
        <span v-if="store.summary">{{ store.summary }}</span>
        <span v-else class="italic text-gray-500"
          >Hikaye ilerledikçe evren özeti (Running Summary) burada oluşacak...</span
        >
      </div>
    </aside>

    <main class="w-2/4 flex flex-col relative">
      <div
        class="p-4 border-b border-gray-800 bg-gray-900/90 backdrop-blur z-10 flex justify-between items-center"
      >
        <h1 class="text-lg font-semibold text-white">
          {{ store.storyId ? `Aktif Kurgu (ID: ${store.storyId})` : 'Yeni Kurgu' }}
        </h1>
        <span v-if="store.isLoading" class="text-xs text-indigo-400 animate-pulse">
          Yapay Zeka İşliyor...
        </span>
      </div>

      <div class="flex-1 overflow-y-auto p-8 space-y-6 text-lg leading-relaxed whitespace-pre-wrap">
        <div v-if="store.content">{{ store.content }}</div>
        <div v-else-if="!store.isLoading" class="text-gray-500 text-center text-sm mt-10">
          Hikayeye başlamak için aşağıya ilk eylemini veya ortamı yaz...
        </div>
      </div>

      <div class="p-4 bg-gray-900 border-t border-gray-800">
        <div class="flex gap-2">
          <input
            v-model="userInput"
            @keyup.enter="handleAction"
            :disabled="store.isLoading"
            type="text"
            :placeholder="store.storyId ? 'Ne yapacaksın?' : 'Hikayeye nasıl bir giriş yapalım?'"
            class="flex-1 bg-gray-800 text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-indigo-500 border border-gray-700 disabled:opacity-50"
          />
          <button
            @click="handleAction"
            :disabled="store.isLoading || !userInput.trim()"
            class="bg-indigo-600 hover:bg-indigo-700 px-6 py-3 rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ store.storyId ? 'Hamle Yap' : 'Başlat' }}
          </button>
        </div>
      </div>
    </main>

    <aside class="w-1/4 bg-gray-800 border-l border-gray-700 flex flex-col">
      <div class="p-4 border-b border-gray-700">
        <h2 class="text-sm font-bold text-gray-400 uppercase tracking-wider">Evren Elementleri</h2>
      </div>
      <div class="p-4 flex-1 overflow-y-auto space-y-6">
        <p
          v-if="!store.characters.length && !store.locations.length && !store.items.length"
          class="text-xs text-gray-500 italic text-center mt-5"
        >
          Henüz keşfedilen bir element yok.
        </p>

        <div v-if="store.characters.length > 0">
          <h3 class="text-xs font-bold text-gray-400 mb-2 border-b border-gray-700 pb-1">
            KARAKTERLER
          </h3>
          <div
            v-for="(char, index) in store.characters"
            :key="'char-' + index"
            class="bg-gray-700/50 p-2 rounded border border-gray-600 mb-2"
          >
            <h4 class="font-medium text-white text-sm">{{ char.name }}</h4>
            <p class="text-xs text-gray-400">{{ char.description }}</p>
          </div>
        </div>

        <div v-if="store.locations.length > 0">
          <h3 class="text-xs font-bold text-gray-400 mb-2 border-b border-gray-700 pb-1">
            MEKANLAR
          </h3>
          <div
            v-for="(loc, index) in store.locations"
            :key="'loc-' + index"
            class="bg-gray-700/50 p-2 rounded border border-gray-600 mb-2"
          >
            <h4 class="font-medium text-white text-sm">{{ loc.name }}</h4>
            <p class="text-xs text-gray-400">{{ loc.description }}</p>
          </div>
        </div>

        <div v-if="store.items.length > 0">
          <h3 class="text-xs font-bold text-gray-400 mb-2 border-b border-gray-700 pb-1">
            EŞYALAR
          </h3>
          <div
            v-for="(item, index) in store.items"
            :key="'item-' + index"
            class="bg-gray-700/50 p-2 rounded border border-gray-600 mb-2"
          >
            <h4 class="font-medium text-white text-sm">{{ item.name }}</h4>
            <p class="text-xs text-gray-400">{{ item.description }}</p>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style>
::-webkit-scrollbar {
  width: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #4b5563;
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: #6b7280;
}
</style>
