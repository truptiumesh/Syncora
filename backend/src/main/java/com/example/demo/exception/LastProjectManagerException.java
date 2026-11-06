package com.example.demo.exception;

public class LastProjectManagerException extends RuntimeException {
	public LastProjectManagerException() {
		super("Assign another project manager before removing or demoting this member.");
	}
}
