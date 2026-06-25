export interface ElementDto {
  name: string
  description: string
}

export interface StoryDetailResponse {
  id: number
  title: string
  content: string
  status: string
  currentSummary: string | null
  actionCount: number
  characters: ElementDto[]
  locations: ElementDto[]
  items: ElementDto[]
}
