package com.helpdesk.helpdesk.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.dto.auth.ForgotPasswordRequest;
import com.helpdesk.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.helpdesk.dto.auth.RegisterInviteResponse;
import com.helpdesk.helpdesk.dto.auth.RegisterRequest;
import com.helpdesk.helpdesk.dto.auth.ResetPasswordRequest;
import com.helpdesk.helpdesk.dto.common.OperationMessageResponse;
import com.helpdesk.helpdesk.service.AuthService;
import com.helpdesk.helpdesk.service.PasswordRecoveryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final PasswordRecoveryService passwordRecoveryService;

	public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
		this.authService = authService;
		this.passwordRecoveryService = passwordRecoveryService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/register-invite")
	public RegisterInviteResponse getRegisterInvite(@RequestParam String token) {
		return authService.getRegisterInvite(token);
	}

	@PostMapping("/forgot-password")
	public OperationMessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return passwordRecoveryService.requestReset(request);
	}

	@PostMapping("/reset-password")
	public OperationMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		return passwordRecoveryService.resetPassword(request);
	}
}
