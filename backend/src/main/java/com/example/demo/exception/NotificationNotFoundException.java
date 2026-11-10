package com.example.demo.exception;

public class NotificationNotFoundException extends RuntimeException {
	public NotificationNotFoundException() {
		super("Notification not found.");
	}
}
