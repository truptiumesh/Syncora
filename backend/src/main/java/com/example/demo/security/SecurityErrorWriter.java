package com.example.demo.security;

import com.example.demo.dto.response.ApiErrorResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityErrorWriter {
	private final ObjectMapper objectMapper;

	public SecurityErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
				Instant.now(), status, error, message, request.getRequestURI(), Map.of()));
	}
}
