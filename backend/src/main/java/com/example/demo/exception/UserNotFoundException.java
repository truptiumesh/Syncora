package com.example.demo.exception;

public class UserNotFoundException extends RuntimeException {
	public UserNotFoundException() {
		super("User not found.");
	}
}
