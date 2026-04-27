package com.helpdesk.helpdesk.dto.profile;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
	UUID id,
	String fullName,
	String email,
	String phoneNumber,
	String documentNumber,
	String companyName,
	String companyDocument,
	String companyType,
	String status,
	List<String> roles
) {
}
