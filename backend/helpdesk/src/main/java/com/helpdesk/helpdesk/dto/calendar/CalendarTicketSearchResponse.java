package com.helpdesk.helpdesk.dto.calendar;

import java.util.List;

public record CalendarTicketSearchResponse(
	List<CalendarLinkedTicketResponse> tickets,
	boolean hasMore
) {
}
