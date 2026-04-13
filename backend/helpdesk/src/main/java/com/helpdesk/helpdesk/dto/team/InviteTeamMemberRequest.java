package com.helpdesk.helpdesk.dto.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteTeamMemberRequest(
	@NotBlank @Size(min = 3, max = 150) String invitedName,
	@NotBlank @Email String email,
	@NotBlank @Email String invitedByEmail,
	@NotEmpty List<@NotNull UUID> sectorIds
) {
}
