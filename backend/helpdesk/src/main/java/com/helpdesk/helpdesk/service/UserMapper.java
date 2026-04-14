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
		return new AuthResponse(
			user.getId(),
			user.getFullName(),
			user.getEmail(),
			user.getPhoneNumber(),
			user.getDocumentNumber(),
			user.getCompanyName(),
			user.getCompanyDocument(),
			user.getStatus().name(),
			roleCodes(user)
		);
	}

	public ProfileResponse toProfileResponse(User user) {
		return new ProfileResponse(
			user.getId(),
			user.getFullName(),
			user.getEmail(),
			user.getPhoneNumber(),
			user.getDocumentNumber(),
			user.getCompanyName(),
			user.getCompanyDocument(),
			user.getStatus().name(),
			roleCodes(user)
		);
	}

	private List<String> roleCodes(User user) {
		return user.getRoles().stream()
			.map(role -> role.getCode().toLowerCase())
			.sorted(Comparator.naturalOrder())
			.toList();
	}
}
