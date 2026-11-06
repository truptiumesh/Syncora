package com.example.demo.service;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import com.example.demo.security.PasswordPolicy;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (!PasswordPolicy.isWithinBcryptLimit(request.password())) {
			throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
		}
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		User user = new User();
		user.setFullName(request.fullName().trim());
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRole(Role.MEMBER);
		user.setActive(true);

		try {
			user = userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
		return toAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		if (!PasswordPolicy.isWithinBcryptLimit(request.password())) {
			throw new InvalidCredentialsException();
		}
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email)
				.filter(User::isActive)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
				.orElseThrow(InvalidCredentialsException::new);
		return toAuthResponse(user);
	}

	private AuthResponse toAuthResponse(User user) {
		return new AuthResponse(jwtService.generateToken(user), "Bearer", UserResponse.from(user));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
