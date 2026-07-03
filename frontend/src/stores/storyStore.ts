import { defineStore } from 'pinia'
import axios from 'axios'
import type { StoryDetailResponse, CreateStoryRequest } from '../types'

const API_URL = 'http://localhost:8080/api/stories'

export const useStoryStore = defineStore('story', {
  state: () => ({
    story: null as StoryDetailResponse | null,
    myStories: [] as StoryDetailResponse[], // YENİ: Kullanıcının hikayeleri
    isLoading: false,
    error: null as string | null,
    eventSource: null as EventSource | null,
    watchdogTimer: null as number | null,
  }),

  actions: {
    // dashboard'da gösterilecek tüm hikayeleri getirir
    async fetchMyStories() {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.get<StoryDetailResponse[]>(`${API_URL}/my-stories`)
        this.myStories = response.data
      } catch (err: any) {
        this.error = err.message || 'Hikayeler yüklenemedi.'
      } finally {
        this.isLoading = false
      }
    },

    // Sıfırdan hikaye oluşturur ve yönlendirme için döner
    async createStory(payload: CreateStoryRequest) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.post<StoryDetailResponse>(API_URL, payload)
        return response.data
      } catch (err: any) {
        this.error = err.message || 'Hikaye oluşturulamadı.'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    async fetchStory(storyId: number) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.get<StoryDetailResponse>(`${API_URL}/${storyId}`)
        this.story = response.data
        this.connectToStream(storyId)

        // butonları kilitli tut ve Watchdog'u başlat.
        if (this.story.status === 'PENDING') {
          this.isLoading = true
          this.startWatchdog()
        } else {
          this.isLoading = false
        }
      } catch (err: any) {
        this.error = err.message || 'Hikaye yüklenemedi.'
        this.isLoading = false
      }
    },

    async deleteStory(storyId: number) {
      this.isLoading = true
      this.error = null
      try {
        await axios.delete(`${API_URL}/${storyId}`)
        // Silme başarılıysa o hikayeyi ekrandaki listeden anında uçur
        this.myStories = this.myStories.filter((s) => s.id !== storyId)
      } catch (err: any) {
        this.error = err.message || 'Hikaye silinemedi.'
      } finally {
        this.isLoading = false
      }
    },

    connectToStream(storyId: number) {
      this.disconnectStream()

      const token = localStorage.getItem('token')
      this.eventSource = new EventSource(`${API_URL}/${storyId}/stream?token=${token}`)

      this.eventSource.addEventListener('STORY_UPDATE', (event) => {
        const updatedData = JSON.parse(event.data)

        // Hata mesajı varsa
        if (updatedData.type === 'AI_ERROR') {
          this.error = updatedData.message
          this.isLoading = false
          this.stopWatchdog()
          return // İşlemi kes, hikaye metnini bozma
        }

        // NORMAL İŞLEYİŞ: Gelen veri gerçek bir hikaye güncellemesiyse
        this.story = updatedData
        this.error = null // Varsa eski hatayı temizle
        this.isLoading = false
        this.stopWatchdog()
      })

      this.eventSource.onerror = (err) => {
        this.disconnectStream()
      }
    },

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
      this.startWatchdog()

      try {
        await axios.post(`${API_URL}/${storyId}/continue`, {
          userAction: userAction,
        })
      } catch (err: any) {
        this.error = err.message || 'Hamle gönderilemedi.'
        this.stopWatchdog()
        this.isLoading = false // KRİTİK: Hata alırsan butonu kilitten kurtar
      }
    },

    startWatchdog() {
      this.stopWatchdog()
      this.watchdogTimer = window.setTimeout(() => {
        if (this.isLoading) {
          this.error = 'Yapay zeka çok yoğun, yanıt gecikti. Lütfen sayfayı yenileyin.'
          this.isLoading = false
          this.disconnectStream() // Tarayıcı (canceled) hatasının ana sebebi bu satırdır
        }
      }, 180000) // 60000'den 180000'e (3 dakika) çıkardık!
    },

    stopWatchdog() {
      if (this.watchdogTimer) {
        clearTimeout(this.watchdogTimer)
        this.watchdogTimer = null
      }
    },
    async editStory(storyId: number, newContent: string) {
      this.isLoading = true
      this.error = null
      try {
        const response = await axios.put(`${API_URL}/${storyId}/content`, { newContent })
        this.story = response.data
      } catch (err: any) {
        this.error = err.message || 'Hikaye güncellenemedi.'
      } finally {
        this.isLoading = false
      }
    },
  },
})
