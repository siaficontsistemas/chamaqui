package com.helpdesk.helpdesk.tenant;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.helpdesk.helpdesk.service.TenantResolutionService;
import com.helpdesk.helpdesk.util.RequestHostResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

	private final TenantResolutionService tenantResolutionService;

	public TenantResolutionFilter(TenantResolutionService tenantResolutionService) {
		this.tenantResolutionService = tenantResolutionService;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			String resolvedHost = RequestHostResolver.resolveTenantHost(request);
			tenantResolutionService.resolve(resolvedHost).ifPresent(TenantContext::set);
			filterChain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}
}
