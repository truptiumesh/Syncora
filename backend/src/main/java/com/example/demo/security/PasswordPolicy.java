package com.example.demo.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
	private static final int MAX_BCRYPT_BYTES = 72;

	private PasswordPolicy() {
	}

	public static boolean isWithinBcryptLimit(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
	}
}
