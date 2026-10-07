package com.helpdesk.helpdesk.dto.client;

import java.util.UUID;

public record PreRegisterClientResponse(UUID id, String fullName, String email, String phoneNumber, String companyName) {}
