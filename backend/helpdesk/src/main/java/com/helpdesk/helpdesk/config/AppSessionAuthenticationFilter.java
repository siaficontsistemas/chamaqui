package com.helpdesk.helpdesk.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.helpdesk.helpdesk.service.AppSessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class AppSessionAuthenticationFilter extends OncePerRequestFilter {

	private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
		"/api/v1/public/",
		"/api/v1/reference/",
		"/api/v1/platform-admin/"
	);

	private static final List<String> PUBLIC_PATHS = List.of(
		"/api/v1/auth/login",
		"/api/v1/auth/register",
		"/api/v1/auth/register-invite",
		"/api/v1/auth/forgot-password",
		"/api/v1/auth/reset-password"
	);

	private final AppSessionService appSessionService;

	public AppSessionAuthenticationFilter(AppSessionService appSessionService) {
		this.appSessionService = appSessionService;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (shouldSkip(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session == null || !appSessionService.hasAuthenticatedUser(session)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"message\":\"Faça login para continuar.\"}");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean shouldSkip(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return true;
		}
		if (!path.startsWith("/api/v1/")) {
			return true;
		}
		if (PUBLIC_PATHS.contains(path)) {
			return true;
		}
		return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
	}
}
