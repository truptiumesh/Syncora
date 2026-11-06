package com.example.demo.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {
	public WorkspaceMemberNotFoundException() {
		super("Workspace member not found.");
	}
}
