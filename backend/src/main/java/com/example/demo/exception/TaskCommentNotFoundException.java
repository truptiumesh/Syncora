package com.example.demo.exception;

public class TaskCommentNotFoundException extends RuntimeException {
	public TaskCommentNotFoundException() {
		super("Task comment not found.");
	}
}
