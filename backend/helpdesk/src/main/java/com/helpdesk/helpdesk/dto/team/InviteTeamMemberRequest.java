package com.helpdesk.helpdesk.dto.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteTeamMemberRequest(
	@NotBlank @Size(min = 11, max = 20) String documentNumber,
	@NotBlank String invitedByEmail,
	@NotEmpty List<@NotNull UUID> sectorIds
) {
}
