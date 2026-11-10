package com.example.demo.exception;

public class DirectConversationNotFoundException extends RuntimeException {
	public DirectConversationNotFoundException() {
		super("Direct conversation was not found.");
	}
}
