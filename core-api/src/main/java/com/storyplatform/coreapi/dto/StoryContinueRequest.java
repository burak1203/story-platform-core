package com.storyplatform.coreapi.dto;
import lombok.Data;

@Data
public class StoryContinueRequest {
    private String userAction;
    // userId sildik! Artık token'dan alacağız.
}