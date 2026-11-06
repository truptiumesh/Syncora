package com.example.demo.controller;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public UserResponse getProfile(Authentication authentication) {
		return userService.getProfile(authentication.getName());
	}

	@PutMapping
	public UserResponse updateProfile(
			Authentication authentication,
			@Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(authentication.getName(), request);
	}

	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(
			Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(authentication.getName(), request);
		return ResponseEntity.noContent().build();
	}
}
