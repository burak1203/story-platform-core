import { defineStore } from 'pinia'
import axios from 'axios'
import type { StoryDetailResponse } from '../types'

const API_URL = 'http://localhost:8080/api/stories'

export const useStoryStore = defineStore('story', {
  state: () => ({
    story: null as StoryDetailResponse | null,
    isLoading: false,
    error: null as string | null,
    eventSource: null as EventSource | null,
    watchdogTimer: null as number | null, // Kara deliği engellemek için zamanlayıcı
  }),

  actions: {
    async fetchStory(storyId: number) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.get<StoryDetailResponse>(`${API_URL}/${storyId}`)
        this.story = response.data
        this.connectToStream(storyId)
      } catch (err: any) {
        this.error = err.message || 'Hikaye yüklenemedi.'
      } finally {
        this.isLoading = false
      }
    },

    connectToStream(storyId: number) {
      this.disconnectStream() // Önce eskisini temizle (Zombileri engelle)

      this.eventSource = new EventSource(`${API_URL}/${storyId}/stream`)

      this.eventSource.addEventListener('STORY_UPDATE', (event) => {
        console.log("Backend'den güncel hikaye canlı olarak geldi!")
        this.story = JSON.parse(event.data)
        this.stopWatchdog() // Veri geldiyse zamanlayıcıyı iptal et
        this.isLoading = false
      })

      this.eventSource.onerror = (err) => {
        console.error('SSE Bağlantı Hatası veya Tünel Koptu.')
        // Sonsuz döngüye girmemesi için tüneli kapatıyoruz
        this.disconnectStream()
      }
    },

    // YENİ: Uygulama kapanırken veya bileşen ölürken çağrılacak
    disconnectStream() {
      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
      this.stopWatchdog()
    },

    async continueStory(storyId: number, userAction: string) {
      this.isLoading = true
      this.error = null

      this.startWatchdog() // 60 saniyelik sigortayı başlat

      try {
        await axios.post(`${API_URL}/${storyId}/continue`, {
          userId: 1,
          userAction: userAction,
        })
      } catch (err: any) {
        this.error = err.message || 'Hamle gönderilemedi.'
        this.stopWatchdog()
        this.isLoading = false
      }
    },

    // YENİ: Kara delik koruması
    startWatchdog() {
      this.stopWatchdog()
      this.watchdogTimer = window.setTimeout(() => {
        if (this.isLoading) {
          this.error =
            'Yapay zeka çok uzun süre yanıt vermedi. Lütfen tekrar deneyin veya sayfayı yenileyin.'
          this.isLoading = false
          this.disconnectStream() // Arızalı tüneli kes
        }
      }, 60000) // 60 saniye bekleme süresi
    },

    stopWatchdog() {
      if (this.watchdogTimer) {
        clearTimeout(this.watchdogTimer)
        this.watchdogTimer = null
      }
    },
  },
})
