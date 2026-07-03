package com.storyplatform.coreapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElementDto {
    private Long id; // KRİTİK: Düzenleme ve silme için ID eklendi
    private String name;
    private String description;
}