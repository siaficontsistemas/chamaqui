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
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.AuthService;
import com.helpdesk.helpdesk.service.PasswordRecoveryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final AppSessionService appSessionService;
	private final PasswordRecoveryService passwordRecoveryService;

	public AuthController(
		AuthService authService,
		AppSessionService appSessionService,
		PasswordRecoveryService passwordRecoveryService
	) {
		this.authService = authService;
		this.appSessionService = appSessionService;
		this.passwordRecoveryService = passwordRecoveryService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(
		@Valid @RequestBody RegisterRequest request,
		HttpSession session,
		HttpServletRequest httpRequest
	) {
		return authService.register(request, session, httpRequest);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) {
		return authService.login(request, session);
	}

	@GetMapping("/me")
	public AuthResponse me(HttpSession session) {
		return appSessionService.me(session);
	}

	@PostMapping("/logout")
	public OperationMessageResponse logout(HttpSession session) {
		appSessionService.logout(session);
		return new OperationMessageResponse("Sessão encerrada com sucesso.");
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
