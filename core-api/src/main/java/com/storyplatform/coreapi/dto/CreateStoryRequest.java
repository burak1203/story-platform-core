package com.storyplatform.coreapi.dto;
import lombok.Data;

@Data
public class CreateStoryRequest {
    private String title;
    private String startingPrompt; // Örn: "Karanlık bir ormanda uyandım."
}