package com.helpdesk.helpdesk.dto.auth;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
	UUID id,
	String fullName,
	String email,
	String phoneNumber,
	String documentNumber,
	String companyName,
	String companyDocument,
	String status,
	List<String> roles
) {
}
