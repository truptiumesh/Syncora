package com.example.demo.service;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.PasswordMismatchException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.PasswordPolicy;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public UserResponse getProfile(String email) {
		return UserResponse.from(findUser(email));
	}

	@Transactional
	public UserResponse updateProfile(String email, UpdateProfileRequest request) {
		User user = findUser(email);
		user.setFullName(request.fullName().trim());
		user.setBio(normalizeOptional(request.bio()));
		user.setProfileImageUrl(normalizeOptional(request.profileImageUrl()));
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public void changePassword(String email, ChangePasswordRequest request) {
		if (!PasswordPolicy.isWithinBcryptLimit(request.newPassword())) {
			throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
		}
		User user = findUser(email);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new PasswordMismatchException();
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
			throw new IllegalArgumentException("The new password must be different from the current password.");
		}
		user.setPassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	private User findUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(UserNotFoundException::new);
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
