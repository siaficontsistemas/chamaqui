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
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.CalendarService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

	private final CalendarService calendarService;
	private final AppSessionService appSessionService;

	public CalendarController(CalendarService calendarService, AppSessionService appSessionService) {
		this.calendarService = calendarService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/obligations")
	public List<CalendarObligationResponse> list(HttpSession session) {
		return calendarService.listVisible(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/companies")
	public List<CalendarLinkedCompanyResponse> listLinkedCompanies(HttpSession session) {
		return calendarService.listLinkedCompanies(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/tickets/search")
	public CalendarTicketSearchResponse searchTickets(
		@RequestParam(required = false, defaultValue = "") String query,
		@RequestParam(required = false, defaultValue = "0") int offset,
		@RequestParam(required = false, defaultValue = "20") int limit,
		HttpSession session
	) {
		return calendarService.searchTickets(appSessionService.requireCurrentEmail(session), query, offset, limit);
	}

	@PostMapping("/obligations")
	@ResponseStatus(HttpStatus.CREATED)
	public CalendarObligationResponse create(
		@Valid @RequestBody CreateCalendarObligationRequest request,
		HttpSession session
	) {
		return calendarService.create(
			new CreateCalendarObligationRequest(
				request.title(),
				request.description(),
				request.dueAt(),
				request.reminderAt(),
				request.priority(),
				request.linkedCompanyOwnerId(),
				request.linkedTicketIds(),
				request.recipientDocumentNumbers(),
				appSessionService.requireCurrentEmail(session)
			)
		);
	}

	@PutMapping("/obligations/{obligationId}")
	public CalendarObligationResponse update(
		@PathVariable UUID obligationId,
		@Valid @RequestBody UpdateCalendarObligationRequest request,
		HttpSession session
	) {
		return calendarService.update(
			obligationId,
			new UpdateCalendarObligationRequest(
				request.title(),
				request.description(),
				request.dueAt(),
				request.reminderAt(),
				request.priority(),
				request.linkedCompanyOwnerId(),
				request.linkedTicketIds(),
				request.recipientDocumentNumbers(),
				appSessionService.requireCurrentEmail(session)
			)
		);
	}

	@PutMapping("/obligations/{obligationId}/linked-tickets")
	public CalendarObligationResponse updateLinkedTickets(
		@PathVariable UUID obligationId,
		@Valid @RequestBody UpdateCalendarObligationTicketsRequest request,
		HttpSession session
	) {
		return calendarService.updateLinkedTickets(
			obligationId,
			new UpdateCalendarObligationTicketsRequest(request.linkedTicketIds(), appSessionService.requireCurrentEmail(session))
		);
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
	public void complete(@PathVariable UUID obligationId, HttpSession session) {
		calendarService.complete(obligationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/obligations/{obligationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID obligationId, HttpSession session) {
		calendarService.delete(obligationId, appSessionService.requireCurrentEmail(session));
	}
}
