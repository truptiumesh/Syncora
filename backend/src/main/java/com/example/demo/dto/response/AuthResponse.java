package com.example.demo.dto.response;

public record AuthResponse(String token, String tokenType, UserResponse user) {
}
