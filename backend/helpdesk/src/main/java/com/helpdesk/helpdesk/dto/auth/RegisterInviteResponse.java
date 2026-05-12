package com.helpdesk.helpdesk.dto.auth;

public record RegisterInviteResponse(
	String fullName,
	String email,
	String documentNumber,
	String companyName,
	String companyType,
	String participation,
	String role
) {
}
