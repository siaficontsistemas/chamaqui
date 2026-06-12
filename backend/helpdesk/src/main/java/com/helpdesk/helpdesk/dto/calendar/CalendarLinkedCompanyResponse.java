package com.helpdesk.helpdesk.dto.calendar;

import java.util.UUID;

public record CalendarLinkedCompanyResponse(
	UUID id,
	String name,
	String companyType
) {
}
