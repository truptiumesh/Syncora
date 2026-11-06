package com.example.demo.exception;

public class TaskNotFoundException extends RuntimeException {
	public TaskNotFoundException() {
		super("Task not found.");
	}
}
