import { defineStore } from 'pinia'
import axios from 'axios'
import type { StoryDetailResponse, ElementDto } from '../types'

// Java Core API varsayılan portu 8080.
const API_URL = 'http://localhost:8080/api/stories'

export const useStoryStore = defineStore('story', {
  state: () => ({
    storyId: null as number | null,
    content: '' as string,
    summary: '' as string,
    characters: [] as ElementDto[],
    locations: [] as ElementDto[],
    items: [] as ElementDto[],
    isLoading: false as boolean,
  }),

  actions: {
    async startNewStory(prompt: string) {
      this.isLoading = true
      try {
        const response = await axios.post<StoryDetailResponse>(`${API_URL}/generate`, {
          userId: 1,
          title: 'Yeni Macera',
          prompt: prompt,
        })
        this.updateState(response.data)
      } catch (error) {
        console.error("Hikaye başlatılırken backend'e ulaşılamadı:", error)
      } finally {
        this.isLoading = false
      }
    },

    async continueStory(userAction: string) {
      if (!this.storyId) return
      this.isLoading = true
      try {
        const response = await axios.post<StoryDetailResponse>(
          `${API_URL}/${this.storyId}/continue`,
          {
            userId: 1,
            userAction: userAction,
          },
        )
        this.updateState(response.data)
      } catch (error) {
        console.error('Hikaye devam ettirilirken hata oluştu:', error)
      } finally {
        this.isLoading = false
      }
    },

    updateState(data: StoryDetailResponse) {
      this.storyId = data.id
      this.content = data.content
      this.summary = data.summary || 'Özet henüz oluşturulmadı...'
      this.characters = data.characters || []
      this.locations = data.locations || []
      this.items = data.items || []
    },
  },
})
