package com.example.demo.exception;

public class InvalidTaskAssigneeException extends RuntimeException {
	public InvalidTaskAssigneeException() {
		super("The assignee must be an active member of this project.");
	}
}
