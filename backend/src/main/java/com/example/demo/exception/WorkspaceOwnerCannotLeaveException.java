package com.example.demo.exception;

public class WorkspaceOwnerCannotLeaveException extends RuntimeException {
	public WorkspaceOwnerCannotLeaveException() {
		super("Transfer workspace ownership before leaving the workspace.");
	}
}
