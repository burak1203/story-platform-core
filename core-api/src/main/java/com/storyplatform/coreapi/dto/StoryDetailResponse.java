package com.storyplatform.coreapi.dto;

import java.util.List;

public record StoryDetailResponse(
        Long id,
        String title,
        String content,
        String status,
        String currentSummary,
        Integer actionCount,
        List<ElementDto> characters,
        List<ElementDto> locations,
        List<ElementDto> items
) {}