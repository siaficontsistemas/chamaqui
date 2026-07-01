package com.helpdesk.helpdesk.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.service.AppAuthTokenService;
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
	private final AppAuthTokenService appAuthTokenService;

	public AppSessionAuthenticationFilter(
		AppSessionService appSessionService,
		AppAuthTokenService appAuthTokenService
	) {
		this.appSessionService = appSessionService;
		this.appAuthTokenService = appAuthTokenService;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		// #region debug-point B:filter-entry
		reportTenantLoginBounceDebug("B", "Filtro de autenticacao recebeu requisicao", """
			{"path":"%s","host":"%s","hasAuthorization":%s}
			""".formatted(
			sanitizeForJson(request.getRequestURI()),
			sanitizeForJson(request.getHeader("X-Tenant-Host")),
			Boolean.toString(request.getHeader("Authorization") != null && !request.getHeader("Authorization").isBlank())
		));
		// #endregion
		if (shouldSkip(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		String bearerToken = extractBearerToken(request);
		if (bearerToken != null) {
			try {
				User authenticatedUser = appAuthTokenService.authenticate(bearerToken);
				// #region debug-point B:bearer-accepted
				reportTenantLoginBounceDebug("B", "Bearer token aceito pelo filtro", """
					{"path":"%s","userId":"%s"}
					""".formatted(
					sanitizeForJson(request.getRequestURI()),
					authenticatedUser.getId() == null ? "" : authenticatedUser.getId().toString()
				));
				// #endregion
				request.setAttribute(AppSessionService.AUTHENTICATED_REQUEST_USER_ATTRIBUTE, authenticatedUser);
				filterChain.doFilter(request, response);
				return;
			} catch (RuntimeException exception) {
				// #region debug-point B:bearer-rejected
				reportTenantLoginBounceDebug("B", "Bearer token rejeitado pelo filtro", """
					{"path":"%s","message":"%s"}
					""".formatted(
					sanitizeForJson(request.getRequestURI()),
					sanitizeForJson(exception.getMessage())
				));
				// #endregion
				writeUnauthorizedResponse(response);
				return;
			}
		}

		HttpSession session = request.getSession(false);
		if (session == null || !appSessionService.hasAuthenticatedUser(session)) {
			// #region debug-point B:unauthorized-without-bearer
			reportTenantLoginBounceDebug("B", "Requisicao caiu em 401 sem bearer valido", """
				{"path":"%s","hasSession":%s}
				""".formatted(
				sanitizeForJson(request.getRequestURI()),
				Boolean.toString(session != null)
			));
			// #endregion
			writeUnauthorizedResponse(response);
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

	private String extractBearerToken(HttpServletRequest request) {
		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			return null;
		}

		if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
			return null;
		}

		String token = authorizationHeader.substring("Bearer ".length()).trim();
		return token.isBlank() ? null : token;
	}

	private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"Faça login para continuar.\"}");
	}

	// #region debug-point B:filter-helper
	private void reportTenantLoginBounceDebug(String hypothesisId, String msg, String dataJson) {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:7777/event"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("""
				{"sessionId":"tenant-login-bounce","runId":"pre-fix","hypothesisId":"%s","location":"backend/AppSessionAuthenticationFilter.java","msg":"[DEBUG] %s","data":%s}
				""".formatted(
				sanitizeForJson(hypothesisId),
				sanitizeForJson(msg),
				dataJson
			)))
			.build();
		HttpClient.newHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding());
	}

	private String sanitizeForJson(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
	// #endregion
}
