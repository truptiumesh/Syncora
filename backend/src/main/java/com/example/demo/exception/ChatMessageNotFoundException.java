package com.example.demo.exception;

public class ChatMessageNotFoundException extends RuntimeException {
	public ChatMessageNotFoundException() {
		super("Chat message was not found.");
	}
}
