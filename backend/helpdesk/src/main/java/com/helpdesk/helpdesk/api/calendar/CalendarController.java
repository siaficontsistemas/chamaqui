package com.helpdesk.helpdesk.api.calendar;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.calendar.CalendarLinkedCompanyResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarObligationResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarTicketSearchResponse;
import com.helpdesk.helpdesk.dto.calendar.CreateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.MoveCalendarObligationCompanyRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationTicketsRequest;
import com.helpdesk.helpdesk.service.CalendarService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

	private final CalendarService calendarService;

	public CalendarController(CalendarService calendarService) {
		this.calendarService = calendarService;
	}

	@GetMapping("/obligations")
	public List<CalendarObligationResponse> list(@RequestParam String email) {
		return calendarService.listVisible(email);
	}

	@GetMapping("/companies")
	public List<CalendarLinkedCompanyResponse> listLinkedCompanies(@RequestParam String email) {
		return calendarService.listLinkedCompanies(email);
	}

	@GetMapping("/tickets/search")
	public CalendarTicketSearchResponse searchTickets(
		@RequestParam String email,
		@RequestParam(required = false, defaultValue = "") String query,
		@RequestParam(required = false, defaultValue = "0") int offset,
		@RequestParam(required = false, defaultValue = "20") int limit
	) {
		return calendarService.searchTickets(email, query, offset, limit);
	}

	@PostMapping("/obligations")
	@ResponseStatus(HttpStatus.CREATED)
	public CalendarObligationResponse create(@Valid @RequestBody CreateCalendarObligationRequest request) {
		return calendarService.create(request);
	}

	@PutMapping("/obligations/{obligationId}")
	public CalendarObligationResponse update(
		@PathVariable UUID obligationId,
		@Valid @RequestBody UpdateCalendarObligationRequest request
	) {
		return calendarService.update(obligationId, request);
	}

	@PutMapping("/obligations/{obligationId}/linked-tickets")
	public CalendarObligationResponse updateLinkedTickets(
		@PathVariable UUID obligationId,
		@Valid @RequestBody UpdateCalendarObligationTicketsRequest request
	) {
		return calendarService.updateLinkedTickets(obligationId, request);
	}

	@PatchMapping("/obligations/{obligationId}/linked-company")
	public CalendarObligationResponse moveToLinkedCompany(
		@PathVariable UUID obligationId,
		@Valid @RequestBody MoveCalendarObligationCompanyRequest request
	) {
		return calendarService.moveToLinkedCompany(obligationId, request);
	}

	@PostMapping("/obligations/{obligationId}/complete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void complete(@PathVariable UUID obligationId, @RequestParam String email) {
		calendarService.complete(obligationId, email);
	}

	@DeleteMapping("/obligations/{obligationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID obligationId, @RequestParam String email) {
		calendarService.delete(obligationId, email);
	}
}
