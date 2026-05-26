package com.helpdesk.helpdesk.util;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestHostResolver {

	private RequestHostResolver() {
	}

	public static String resolveTenantHost(HttpServletRequest request) {
		if (request == null) {
			return "";
		}

		return firstNonBlank(
			request.getHeader("X-Tenant-Host"),
			extractHost(request.getHeader("Origin")),
			extractHost(request.getHeader("Referer")),
			request.getHeader("X-Forwarded-Host"),
			request.getHeader("Host"),
			request.getServerName()
		);
	}

	public static String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}

		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		return "";
	}

	private static String extractHost(String urlLikeValue) {
		if (urlLikeValue == null || urlLikeValue.isBlank()) {
			return "";
		}

		try {
			URI uri = URI.create(urlLikeValue.trim());
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return "";
			}

			int port = uri.getPort();
			return port > 0 ? host + ":" + port : host;
		} catch (IllegalArgumentException exception) {
			return "";
		}
	}
}
