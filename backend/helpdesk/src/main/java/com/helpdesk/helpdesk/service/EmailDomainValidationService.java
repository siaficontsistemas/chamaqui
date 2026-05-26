package com.helpdesk.helpdesk.service;

import java.net.IDN;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailDomainValidationService {

	private static final Set<String> RESERVED_DOMAINS = Set.of(
		"example.com",
		"example.org",
		"example.net",
		"invalid",
		"localhost",
		"local",
		"test"
	);

	private final String dnsTimeoutInitial;
	private final String dnsTimeoutRetries;

	public EmailDomainValidationService(
		@Value("${app.validation.email-dns-timeout-initial-ms:2000}") int dnsTimeoutInitialMs,
		@Value("${app.validation.email-dns-timeout-retries:1}") int dnsTimeoutRetries
	) {
		this.dnsTimeoutInitial = Integer.toString(Math.max(dnsTimeoutInitialMs, 500));
		this.dnsTimeoutRetries = Integer.toString(Math.max(dnsTimeoutRetries, 1));
	}

	public void ensurePublicEmailDomainExists(String email) {
		String normalizedEmail = normalizeEmail(email);
		String domain = extractDomain(normalizedEmail);
		String asciiDomain = toAsciiDomain(domain);

		if (isReservedDomain(domain, asciiDomain)) {
			throw new IllegalArgumentException("Informe um email com domínio existente.");
		}
		if (hasDnsRecord(asciiDomain, "MX") || hasDnsRecord(asciiDomain, "A") || hasDnsRecord(asciiDomain, "AAAA")) {
			return;
		}

		throw new IllegalArgumentException("Informe um email com domínio existente.");
	}

	private String normalizeEmail(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Informe um email válido.");
		}

		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Informe um email válido.");
		}
		return normalized;
	}

	private String extractDomain(String email) {
		int atIndex = email.lastIndexOf('@');
		if (atIndex <= 0 || atIndex == email.length() - 1) {
			throw new IllegalArgumentException("Informe um email válido.");
		}

		String domain = email.substring(atIndex + 1).trim();
		if (domain.isBlank() || domain.startsWith(".") || domain.endsWith(".")) {
			throw new IllegalArgumentException("Informe um email válido.");
		}
		return domain;
	}

	private String toAsciiDomain(String domain) {
		try {
			return IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Informe um email válido.");
		}
	}

	private boolean isReservedDomain(String domain, String asciiDomain) {
		String normalizedDomain = domain.toLowerCase(Locale.ROOT);
		String normalizedAsciiDomain = asciiDomain.toLowerCase(Locale.ROOT);
		if (RESERVED_DOMAINS.contains(normalizedDomain) || RESERVED_DOMAINS.contains(normalizedAsciiDomain)) {
			return true;
		}

		return normalizedAsciiDomain.endsWith(".localhost")
			|| normalizedAsciiDomain.endsWith(".local")
			|| normalizedAsciiDomain.endsWith(".test")
			|| normalizedAsciiDomain.endsWith(".invalid");
	}

	private boolean hasDnsRecord(String asciiDomain, String recordType) {
		Hashtable<String, String> environment = new Hashtable<>();
		environment.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
		environment.put("com.sun.jndi.dns.timeout.initial", dnsTimeoutInitial);
		environment.put("com.sun.jndi.dns.timeout.retries", dnsTimeoutRetries);

		InitialDirContext context = null;
		try {
			context = new InitialDirContext(environment);
			Attributes attributes = context.getAttributes("dns:/" + asciiDomain, new String[] { recordType });
			Attribute attribute = attributes.get(recordType);
			if (attribute == null || attribute.size() == 0) {
				return false;
			}

			NamingEnumeration<?> values = attribute.getAll();
			while (values.hasMore()) {
				Object value = values.next();
				if (value != null && !value.toString().isBlank()) {
					return true;
				}
			}
			return false;
		} catch (NamingException exception) {
			return false;
		} finally {
			if (context != null) {
				try {
					context.close();
				} catch (NamingException ignored) {
					// Ignore cleanup failures from DNS lookups.
				}
			}
		}
	}
}
