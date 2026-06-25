export interface ElementDto {
  name: string
  description: string
}

export interface StoryDetailResponse {
  id: number
  content: string
  summary: string
  characters: ElementDto[]
  locations: ElementDto[]
  items: ElementDto[]
}
