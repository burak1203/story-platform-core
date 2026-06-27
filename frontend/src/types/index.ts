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

export interface RegisterRequest {
  username: string
  email?: string
  password: string
}

export interface AuthenticationRequest {
  username: string
  password: string
}

export interface AuthenticationResponse {
  token: string
}

export interface CreateStoryRequest {
  title: string
  startingPrompt: string
}