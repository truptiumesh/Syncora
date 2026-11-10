package com.example.demo.exception;

public class FileAttachmentNotFoundException extends RuntimeException {
	public FileAttachmentNotFoundException() {
		super("File attachment was not found.");
	}
}
