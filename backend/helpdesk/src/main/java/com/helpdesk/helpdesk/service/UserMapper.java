package com.helpdesk.helpdesk.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.dto.profile.ProfileResponse;

@Component
public class UserMapper {

	public AuthResponse toAuthResponse(User user) {
		return toAuthResponse(user, null);
	}

	public AuthResponse toAuthResponse(User user, String authToken) {
		return new AuthResponse(
			user.getId(),
			user.getFullName(),
			user.getEmail(),
			resolveCompanyName(user),
			resolveCompanyType(user),
			user.getStatus().name(),
			roleCodes(user),
			authToken
		);
	}

	public ProfileResponse toProfileResponse(User user) {
		return new ProfileResponse(
			user.getId(),
			user.getFullName(),
			user.getEmail(),
			user.getPhoneNumber(),
			user.getDocumentNumber(),
			resolveCompanyName(user),
			resolveCompanyDocument(user),
			resolveCompanyType(user),
			user.getStatus().name(),
			roleCodes(user)
		);
	}

	private String resolveCompanyName(User user) {
		if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
			return user.getCompanyName();
		}
		if (user.getCompanyOwner() == null) {
			return null;
		}
		return user.getCompanyOwner().getCompanyName();
	}

	private String resolveCompanyDocument(User user) {
		if (user.getCompanyDocument() != null && !user.getCompanyDocument().isBlank()) {
			return user.getCompanyDocument();
		}
		if (user.getCompanyOwner() == null) {
			return null;
		}
		return user.getCompanyOwner().getCompanyDocument();
	}

	private String resolveCompanyType(User user) {
		if (user.getCompanyType() != null) {
			return user.getCompanyType().name();
		}
		if (user.getCompanyOwner() == null || user.getCompanyOwner().getCompanyType() == null) {
			return null;
		}
		return user.getCompanyOwner().getCompanyType().name();
	}

	private List<String> roleCodes(User user) {
		return user.getRoles().stream()
			.map(role -> role.getCode().toLowerCase())
			.sorted(Comparator.naturalOrder())
			.toList();
	}
}
