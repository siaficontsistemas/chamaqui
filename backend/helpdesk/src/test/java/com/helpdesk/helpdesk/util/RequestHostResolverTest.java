package com.helpdesk.helpdesk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestHostResolverTest {

	@Test
	void shouldPreferTenantHostQueryParameterWhenPresent() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("tenantHost", "lopesconsultoria.chamaqui.app.br");
		request.addHeader("Host", "api.chamaqui.app.br");
		request.addHeader("Referer", "https://siaficontsistemas.chamaqui.app.br/tickets/123");

		assertEquals("lopesconsultoria.chamaqui.app.br", RequestHostResolver.resolveTenantHost(request));
	}
}
