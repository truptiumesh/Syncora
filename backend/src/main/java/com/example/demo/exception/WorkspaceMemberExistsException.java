package com.example.demo.exception;

public class WorkspaceMemberExistsException extends RuntimeException {
	public WorkspaceMemberExistsException() {
		super("This user is already a member of the workspace.");
	}
}
