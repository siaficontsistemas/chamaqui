package com.helpdesk.helpdesk.dto.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RespondTeamInviteRequest(
	@NotBlank @Email String email
) {
}
