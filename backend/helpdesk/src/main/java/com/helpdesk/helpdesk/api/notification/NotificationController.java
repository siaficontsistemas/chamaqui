package com.helpdesk.helpdesk.api.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.company.RespondCompanyAccessRequest;
import com.helpdesk.helpdesk.dto.notification.CalendarReminderNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyAccessRequestNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyAdminInviteNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyPartnershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
import com.helpdesk.helpdesk.service.CompanyAccessRequestService;
import com.helpdesk.helpdesk.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final CompanyAccessRequestService companyAccessRequestService;

	public NotificationController(
		NotificationService notificationService,
		CompanyAccessRequestService companyAccessRequestService
	) {
		this.notificationService = notificationService;
		this.companyAccessRequestService = companyAccessRequestService;
	}

	@GetMapping("/ticket-assignments")
	public List<TicketAssignmentNotificationResponse> listTicketAssignments(@RequestParam String email) {
		return notificationService.listTicketAssignments(email);
	}

	@GetMapping("/ticket-transfers")
	public List<TicketTransferNotificationResponse> listTicketTransfers(@RequestParam String email) {
		return notificationService.listTicketTransfers(email);
	}

	@GetMapping("/team-memberships")
	public List<TeamMembershipNotificationResponse> listTeamMemberships(@RequestParam String email) {
		return notificationService.listTeamMemberships(email);
	}

	@GetMapping("/calendar-reminders")
	public List<CalendarReminderNotificationResponse> listCalendarReminders(@RequestParam String email) {
		return notificationService.listCalendarReminders(email);
	}

	@GetMapping("/company-partnerships")
	public List<CompanyPartnershipNotificationResponse> listCompanyPartnerships(@RequestParam String email) {
		return notificationService.listCompanyPartnerships(email);
	}

	@GetMapping("/company-access-requests")
	public List<CompanyAccessRequestNotificationResponse> listCompanyAccessRequests(@RequestParam String email) {
		return companyAccessRequestService.listPendingNotifications(email);
	}

	@GetMapping("/company-invites")
	public List<CompanyAdminInviteNotificationResponse> listCompanyInvites(@RequestParam String email) {
		return companyAccessRequestService.listPendingAdminInvites(email);
	}

	@DeleteMapping("/ticket-assignments/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketAssignment(@PathVariable UUID notificationId, @RequestParam String email) {
		notificationService.deleteTicketAssignment(notificationId, email);
	}

	@PostMapping("/ticket-transfers/{notificationId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptTicketTransfer(
		@PathVariable UUID notificationId,
		@Valid @RequestBody RespondTicketTransferRequest request
	) {
		notificationService.acceptTicketTransfer(notificationId, request);
	}

	@PostMapping("/ticket-transfers/{notificationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineTicketTransfer(
		@PathVariable UUID notificationId,
		@Valid @RequestBody RespondTicketTransferRequest request
	) {
		notificationService.declineTicketTransfer(notificationId, request);
	}

	@PostMapping("/company-access-requests/{requestId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptCompanyAccessRequest(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request
	) {
		companyAccessRequestService.accept(requestId, request);
	}

	@PostMapping("/company-access-requests/{requestId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineCompanyAccessRequest(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request
	) {
		companyAccessRequestService.decline(requestId, request);
	}

	@PostMapping("/company-invites/{requestId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptCompanyInvite(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request
	) {
		companyAccessRequestService.acceptAdminInvite(requestId, request);
	}

	@PostMapping("/company-invites/{requestId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineCompanyInvite(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request
	) {
		companyAccessRequestService.declineAdminInvite(requestId, request);
	}

	@DeleteMapping("/ticket-transfers/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketTransfer(@PathVariable UUID notificationId, @RequestParam String email) {
		notificationService.deleteTicketTransfer(notificationId, email);
	}

	@DeleteMapping("/team-memberships/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTeamMembership(@PathVariable UUID notificationId, @RequestParam String email) {
		notificationService.deleteTeamMembership(notificationId, email);
	}

	@DeleteMapping("/calendar-reminders/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCalendarReminder(@PathVariable UUID notificationId, @RequestParam String email) {
		notificationService.deleteCalendarReminder(notificationId, email);
	}

	@DeleteMapping("/company-partnerships/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCompanyPartnership(@PathVariable UUID notificationId, @RequestParam String email) {
		notificationService.deleteCompanyPartnership(notificationId, email);
	}
}
