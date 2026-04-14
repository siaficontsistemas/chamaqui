package com.helpdesk.helpdesk.dto.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberSectorsRequest(
	@NotBlank @Email String assignedByEmail,
	@NotNull List<@NotNull UUID> sectorIds
) {
}
