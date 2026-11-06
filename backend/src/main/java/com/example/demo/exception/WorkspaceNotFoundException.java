package com.example.demo.exception;

public class WorkspaceNotFoundException extends RuntimeException {
	public WorkspaceNotFoundException() {
		super("Workspace not found.");
	}
}
