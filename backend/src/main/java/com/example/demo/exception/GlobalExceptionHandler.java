package com.example.demo.exception;

import com.example.demo.dto.response.ApiErrorResponse;
import com.example.demo.exception.LastProjectManagerException;
import com.example.demo.exception.ProjectMemberExistsException;
import com.example.demo.exception.ProjectMemberNotFoundException;
import com.example.demo.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.TypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
			DuplicateEmailException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
			InvalidCredentialsException exception, HttpServletRequest request) {
		return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleUserNotFound(
			UserNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler({
			WorkspaceNotFoundException.class,
			WorkspaceMemberNotFoundException.class,
			ProjectNotFoundException.class,
			ProjectMemberNotFoundException.class
	})
	public ResponseEntity<ApiErrorResponse> handleWorkspaceResourceNotFound(
			RuntimeException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler({
			WorkspaceMemberExistsException.class,
			WorkspaceOwnerCannotLeaveException.class,
			ProjectMemberExistsException.class,
			LastProjectManagerException.class
	})
	public ResponseEntity<ApiErrorResponse> handleWorkspaceConflict(
			RuntimeException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(PasswordMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handlePasswordMismatch(
			PasswordMismatchException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(
			IllegalArgumentException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		Map<String, String> validationErrors = new TreeMap<>();
		exception.getBindingResult().getFieldErrors().forEach(fieldError ->
				validationErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
		return error(HttpStatus.BAD_REQUEST, "Request validation failed.", request, validationErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleMalformedRequest(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Request body is invalid.", request, Map.of());
	}

	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "A request parameter or path value is invalid.", request, Map.of());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "The request conflicts with an existing record.", request, Map.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(HttpServletRequest request) {
		return error(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.", request, Map.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(
			Exception exception, HttpServletRequest request) {
		logger.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request, Map.of());
	}

	private ResponseEntity<ApiErrorResponse> error(
			HttpStatus status,
			String message,
			HttpServletRequest request,
			Map<String, String> validationErrors) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getRequestURI(),
				validationErrors);
		return ResponseEntity.status(status).body(response);
	}
}
