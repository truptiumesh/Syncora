package com.example.demo.exception;

public class ProjectMemberExistsException extends RuntimeException {
	public ProjectMemberExistsException() {
		super("This user is already a member of the project.");
	}
}
