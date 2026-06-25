<script setup lang="ts">
import { onUnmounted } from 'vue'
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useStoryStore } from './stores/storyStore'
import { marked } from 'marked'

const store = useStoryStore()
const userAction = ref('')
const scrollContainer = ref<HTMLElement | null>(null)

// Test için ID'si 5 olan hikayeyi doğrudan yüklüyoruz
const ACTIVE_STORY_ID = 5

onMounted(() => {
  store.fetchStory(ACTIVE_STORY_ID)
})

onUnmounted(() => {
  store.disconnectStream()
})

// Markdown içeriğini HTML'e çeviriyoruz
const renderedContent = computed(() => {
  if (!store.story?.content) return ''
  return marked.parse(store.story.content, { breaks: true })
})

// Hikaye içeriği değiştiğinde otomatik en aşağı kaydırma
watch(
  () => store.story?.content,
  async () => {
    await nextTick()
    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
    }
  },
)

const submitAction = async () => {
  if (!userAction.value.trim() || store.isLoading) return

  const actionText = userAction.value
  userAction.value = '' // Inputu hemen temizle

  await store.continueStory(ACTIVE_STORY_ID, actionText)
}
</script>

<template>
  <div class="h-screen w-full bg-slate-900 text-gray-200 flex overflow-hidden font-sans">
    <!-- SOL PANEL: Meta ve Özet -->
    <aside class="w-1/4 bg-slate-800 border-r border-slate-700 p-6 flex flex-col">
      <h1 class="text-2xl font-bold text-amber-500 mb-2">
        {{ store.story?.title || 'Yükleniyor...' }}
      </h1>
      <div class="flex items-center gap-2 mb-8 text-sm text-slate-400">
        <span class="bg-slate-700 px-2 py-1 rounded"
          >Durum: {{ store.story?.status || '...' }}</span
        >
        <span class="bg-slate-700 px-2 py-1 rounded"
          >Hamle: {{ store.story?.actionCount || 0 }}</span
        >
      </div>

      <div class="flex-1 overflow-y-auto pr-2">
        <h2 class="text-xs uppercase tracking-wider text-slate-500 font-semibold mb-3">
          Şu Ana Kadarki Özet
        </h2>
        <p
          class="text-sm leading-relaxed text-slate-300 bg-slate-800/50 p-4 rounded-lg border border-slate-700/50"
        >
          {{ store.story?.currentSummary || 'Henüz bir özet oluşturulmadı (3 hamle gerekiyor).' }}
        </p>
      </div>
    </aside>

    <!-- ORTA PANEL: Hikaye Akışı ve Input -->
    <main class="w-2/4 flex flex-col relative bg-slate-950">
      <div
        v-if="store.error"
        class="bg-red-900/50 border border-red-500 text-red-200 p-4 mx-10 mb-4 rounded-lg"
      >
        ⚠️ {{ store.error }}
      </div>
      <!-- Hikaye Metni -->
      <div
        ref="scrollContainer"
        class="flex-1 overflow-y-auto p-10 pb-32 prose prose-invert max-w-none prose-p:leading-relaxed prose-p:mb-6 prose-a:text-amber-500"
        v-html="renderedContent"
      ></div>

      <!-- Aksiyon Alanı -->
      <div
        class="absolute bottom-0 w-full p-6 bg-gradient-to-t from-slate-950 via-slate-950 to-transparent"
      >
        <form @submit.prevent="submitAction" class="flex gap-3 max-w-3xl mx-auto">
          <input
            v-model="userAction"
            type="text"
            placeholder="Bir sonraki hamleyi yaz (Örn: Kapıyı aç ve içeri gir)..."
            class="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-5 py-4 text-gray-100 focus:outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-500 transition-all shadow-lg"
            :disabled="store.isLoading"
          />
          <button
            type="submit"
            class="bg-amber-600 hover:bg-amber-500 text-slate-900 font-bold px-8 py-4 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-lg flex items-center gap-2"
            :disabled="store.isLoading || !userAction.trim()"
          >
            <span v-if="store.isLoading">Yazılıyor...</span>
            <span v-else>Devam Et</span>
          </button>
        </form>
      </div>
    </main>

    <!-- SAĞ PANEL: Lore ve Elementler -->
    <aside
      class="w-1/4 bg-slate-800 border-l border-slate-700 p-6 flex flex-col gap-6 overflow-y-auto"
    >
      <h2 class="text-lg font-bold text-slate-100 border-b border-slate-700 pb-2">Keşfedilenler</h2>

      <!-- Karakterler -->
      <div>
        <h3 class="text-xs uppercase tracking-wider text-amber-500 font-semibold mb-3">
          Karakterler ({{ store.story?.characters.length || 0 }})
        </h3>
        <div class="flex flex-col gap-3">
          <div
            v-for="(char, idx) in store.story?.characters"
            :key="'char-' + idx"
            class="bg-slate-700/40 p-3 rounded border border-slate-600"
          >
            <div class="font-bold text-sm text-slate-200">{{ char.name }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ char.description }}</div>
          </div>
        </div>
      </div>

      <!-- Mekanlar -->
      <div>
        <h3 class="text-xs uppercase tracking-wider text-emerald-500 font-semibold mb-3">
          Mekanlar ({{ store.story?.locations.length || 0 }})
        </h3>
        <div class="flex flex-col gap-3">
          <div
            v-for="(loc, idx) in store.story?.locations"
            :key="'loc-' + idx"
            class="bg-slate-700/40 p-3 rounded border border-slate-600"
          >
            <div class="font-bold text-sm text-slate-200">{{ loc.name }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ loc.description }}</div>
          </div>
        </div>
      </div>

      <!-- Eşyalar -->
      <div>
        <h3 class="text-xs uppercase tracking-wider text-cyan-500 font-semibold mb-3">
          Eşyalar ({{ store.story?.items.length || 0 }})
        </h3>
        <div class="flex flex-col gap-3">
          <div
            v-for="(item, idx) in store.story?.items"
            :key="'item-' + idx"
            class="bg-slate-700/40 p-3 rounded border border-slate-600"
          >
            <div class="font-bold text-sm text-slate-200">{{ item.name }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ item.description }}</div>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>
