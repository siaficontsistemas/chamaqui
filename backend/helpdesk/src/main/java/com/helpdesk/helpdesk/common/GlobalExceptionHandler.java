package com.helpdesk.helpdesk.common;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.helpdesk.helpdesk.dto.common.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
		return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(UnauthorizedException.class)
	ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception) {
		return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return buildResponse(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos.", fieldErrors);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), Map.of());
	}

	private ResponseEntity<ApiErrorResponse> buildResponse(
		HttpStatus status,
		String message,
		Map<String, String> fieldErrors
	) {
		return ResponseEntity.status(status)
			.body(new ApiErrorResponse(message, status.getReasonPhrase(), OffsetDateTime.now(), fieldErrors));
	}
}
