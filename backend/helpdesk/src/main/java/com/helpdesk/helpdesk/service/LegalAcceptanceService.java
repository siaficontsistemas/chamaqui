package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.LegalAcceptance;
import com.helpdesk.helpdesk.domain.LegalDocumentType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.repository.LegalAcceptanceRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class LegalAcceptanceService {

	private static final String ACCEPTANCE_SOURCE_REGISTRATION = "REGISTRATION";

	private final LegalAcceptanceRepository legalAcceptanceRepository;
	private final LegalDocumentService legalDocumentService;

	public LegalAcceptanceService(
		LegalAcceptanceRepository legalAcceptanceRepository,
		LegalDocumentService legalDocumentService
	) {
		this.legalAcceptanceRepository = legalAcceptanceRepository;
		this.legalDocumentService = legalDocumentService;
	}

	@Transactional
	public void recordRegistrationAcceptances(User user, HttpServletRequest request) {
		if (user == null || user.getId() == null) {
			return;
		}

		String evidenceIp = resolveEvidenceIp(request);
		String evidenceUserAgent = truncate(resolveHeader(request, "User-Agent"), 255);
		OffsetDateTime acceptedAt = OffsetDateTime.now();

		List.of(LegalDocumentType.TERMS_OF_USE, LegalDocumentType.PRIVACY_POLICY)
			.forEach(documentType -> legalAcceptanceRepository.save(buildAcceptance(
				user,
				documentType,
				legalDocumentService.getCurrentVersion(documentType),
				acceptedAt,
				evidenceIp,
				evidenceUserAgent
			)));
	}

	private LegalAcceptance buildAcceptance(
		User user,
		LegalDocumentType documentType,
		String version,
		OffsetDateTime acceptedAt,
		String evidenceIp,
		String evidenceUserAgent
	) {
		LegalAcceptance acceptance = new LegalAcceptance();
		acceptance.setUser(user);
		acceptance.setDocumentType(documentType);
		acceptance.setVersion(version);
		acceptance.setAcceptedAt(acceptedAt);
		acceptance.setEvidenceIp(evidenceIp);
		acceptance.setEvidenceUserAgent(evidenceUserAgent);
		acceptance.setSource(ACCEPTANCE_SOURCE_REGISTRATION);
		return acceptance;
	}

	private String resolveEvidenceIp(HttpServletRequest request) {
		String forwardedFor = resolveHeader(request, "X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			String[] entries = forwardedFor.split(",");
			if (entries.length > 0 && !entries[0].trim().isBlank()) {
				return truncate(entries[0].trim(), 80);
			}
		}
		if (request == null) {
			return null;
		}
		return truncate(request.getRemoteAddr(), 80);
	}

	private String resolveHeader(HttpServletRequest request, String headerName) {
		if (request == null || headerName == null || headerName.isBlank()) {
			return null;
		}
		return request.getHeader(headerName);
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalizedValue = value.trim();
		if (normalizedValue.length() <= maxLength) {
			return normalizedValue;
		}
		return normalizedValue.substring(0, maxLength);
	}
}
