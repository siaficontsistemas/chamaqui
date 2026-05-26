package com.helpdesk.helpdesk.dto.platformadmin;

import java.util.UUID;

public record PlatformAdminAuthResponse(
	UUID id,
	String fullName,
	String email
) {
}
