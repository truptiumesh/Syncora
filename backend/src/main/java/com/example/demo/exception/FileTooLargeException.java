package com.example.demo.exception;

public class FileTooLargeException extends RuntimeException {
	public FileTooLargeException() {
		super("The file exceeds the configured upload size limit.");
	}
}
