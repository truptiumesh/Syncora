package com.example.demo.exception;

public class ProjectNotFoundException extends RuntimeException {
	public ProjectNotFoundException() {
		super("Project not found.");
	}
}
