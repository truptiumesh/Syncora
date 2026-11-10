package com.example.demo.exception;

public class UnsupportedFileTypeException extends RuntimeException {
	public UnsupportedFileTypeException() {
		super("Only PDF, JPEG, PNG, and GIF files are allowed.");
	}
}
