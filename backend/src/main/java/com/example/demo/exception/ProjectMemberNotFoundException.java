package com.example.demo.exception;

public class ProjectMemberNotFoundException extends RuntimeException {
	public ProjectMemberNotFoundException() {
		super("Project member not found.");
	}
}
