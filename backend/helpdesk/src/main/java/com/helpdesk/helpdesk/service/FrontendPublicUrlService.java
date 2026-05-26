package com.helpdesk.helpdesk.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FrontendPublicUrlService {

	private final String frontendBaseUrl;
	private final String baseDomain;

	public FrontendPublicUrlService(
		@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
		@Value("${app.tenancy.base-domain:chamaqui.app.br}") String baseDomain
	) {
		this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
		this.baseDomain = normalizeBaseDomain(baseDomain);
	}

	public boolean isConfigured() {
		return !frontendBaseUrl.isBlank();
	}

	public String defaultBaseUrl() {
		return frontendBaseUrl;
	}

	public String buildAccessUrl(String subdomain) {
		if (frontendBaseUrl.isBlank() || subdomain == null || subdomain.isBlank() || baseDomain.isBlank()) {
			return frontendBaseUrl;
		}

		try {
			return trimTrailingSlash(
				UriComponentsBuilder.fromUriString(frontendBaseUrl)
					.host(subdomain.trim() + "." + baseDomain)
					.replaceQuery(null)
					.fragment(null)
					.build(true)
					.toUriString()
			);
		} catch (IllegalArgumentException exception) {
			return frontendBaseUrl;
		}
	}

	public String buildUrl(String subdomain, String path, Map<String, ?> queryParameters) {
		String baseUrl = subdomain == null || subdomain.isBlank()
			? frontendBaseUrl
			: buildAccessUrl(subdomain);

		if (baseUrl.isBlank()) {
			return "";
		}

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
		if (path != null && !path.isBlank()) {
			builder.path(path.startsWith("/") ? path : "/" + path);
		}
		if (queryParameters != null) {
			queryParameters.forEach((key, value) -> {
				if (key != null && !key.isBlank() && value != null) {
					builder.queryParam(key, value);
				}
			});
		}

		return builder.build(true).toUriString();
	}

	private String normalizeBaseUrl(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return trimTrailingSlash(value.trim());
	}

	private String normalizeBaseDomain(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().replaceAll("^\\.+|\\.+$", "");
	}

	private String trimTrailingSlash(String value) {
		return value.replaceAll("/+$", "");
	}
}
