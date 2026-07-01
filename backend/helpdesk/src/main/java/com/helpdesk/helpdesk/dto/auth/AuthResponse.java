package com.helpdesk.helpdesk.dto.auth;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
	UUID id,
	String fullName,
	String email,
	String companyName,
	String companyType,
	String status,
	List<String> roles,
	String authToken
) {
}
