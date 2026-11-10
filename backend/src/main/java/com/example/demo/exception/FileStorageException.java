package com.example.demo.exception;

public class FileStorageException extends RuntimeException {
	public FileStorageException() {
		super("File storage is unavailable.");
	}

	public FileStorageException(Throwable cause) {
		super("File storage is unavailable.", cause);
	}
}
