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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.company.RespondCompanyAccessRequest;
import com.helpdesk.helpdesk.dto.notification.CalendarReminderNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyAccessRequestNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyAdminInviteNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyPartnershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketClosureNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketReplyNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.CompanyAccessRequestService;
import com.helpdesk.helpdesk.service.NotificationService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final CompanyAccessRequestService companyAccessRequestService;
	private final AppSessionService appSessionService;

	public NotificationController(
		NotificationService notificationService,
		CompanyAccessRequestService companyAccessRequestService,
		AppSessionService appSessionService
	) {
		this.notificationService = notificationService;
		this.companyAccessRequestService = companyAccessRequestService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/ticket-assignments")
	public List<TicketAssignmentNotificationResponse> listTicketAssignments(HttpSession session) {
		return notificationService.listTicketAssignments(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/ticket-transfers")
	public List<TicketTransferNotificationResponse> listTicketTransfers(HttpSession session) {
		return notificationService.listTicketTransfers(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/ticket-closures")
	public List<TicketClosureNotificationResponse> listTicketClosures(HttpSession session) {
		return notificationService.listTicketClosures(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/ticket-replies")
	public List<TicketReplyNotificationResponse> listTicketReplies(HttpSession session) {
		return notificationService.listTicketReplies(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/team-memberships")
	public List<TeamMembershipNotificationResponse> listTeamMemberships(HttpSession session) {
		return notificationService.listTeamMemberships(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/calendar-reminders")
	public List<CalendarReminderNotificationResponse> listCalendarReminders(HttpSession session) {
		return notificationService.listCalendarReminders(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/company-partnerships")
	public List<CompanyPartnershipNotificationResponse> listCompanyPartnerships(HttpSession session) {
		return notificationService.listCompanyPartnerships(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/company-access-requests")
	public List<CompanyAccessRequestNotificationResponse> listCompanyAccessRequests(HttpSession session) {
		return companyAccessRequestService.listPendingNotifications(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/company-invites")
	public List<CompanyAdminInviteNotificationResponse> listCompanyInvites(HttpSession session) {
		return companyAccessRequestService.listPendingAdminInvites(appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/ticket-assignments/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketAssignment(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteTicketAssignment(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@PostMapping("/ticket-transfers/{notificationId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptTicketTransfer(
		@PathVariable UUID notificationId,
		@Valid @RequestBody RespondTicketTransferRequest request,
		HttpSession session
	) {
		notificationService.acceptTicketTransfer(notificationId, withTransferSessionEmail(session));
	}

	@PostMapping("/ticket-transfers/{notificationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineTicketTransfer(
		@PathVariable UUID notificationId,
		@Valid @RequestBody RespondTicketTransferRequest request,
		HttpSession session
	) {
		notificationService.declineTicketTransfer(notificationId, withTransferSessionEmail(session));
	}

	@PostMapping("/company-access-requests/{requestId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptCompanyAccessRequest(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request,
		HttpSession session
	) {
		companyAccessRequestService.accept(requestId, withCompanyAccessSessionEmail(session));
	}

	@PostMapping("/company-access-requests/{requestId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineCompanyAccessRequest(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request,
		HttpSession session
	) {
		companyAccessRequestService.decline(requestId, withCompanyAccessSessionEmail(session));
	}

	@PostMapping("/company-invites/{requestId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptCompanyInvite(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request,
		HttpSession session
	) {
		companyAccessRequestService.acceptAdminInvite(requestId, withCompanyAccessSessionEmail(session));
	}

	@PostMapping("/company-invites/{requestId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineCompanyInvite(
		@PathVariable UUID requestId,
		@Valid @RequestBody RespondCompanyAccessRequest request,
		HttpSession session
	) {
		companyAccessRequestService.declineAdminInvite(requestId, withCompanyAccessSessionEmail(session));
	}

	@DeleteMapping("/ticket-transfers/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketTransfer(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteTicketTransfer(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/ticket-closures/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketClosure(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteTicketClosure(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/ticket-replies/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTicketReply(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteTicketReply(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/team-memberships/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTeamMembership(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteTeamMembership(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/calendar-reminders/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCalendarReminder(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteCalendarReminder(notificationId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/company-partnerships/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCompanyPartnership(@PathVariable UUID notificationId, HttpSession session) {
		notificationService.deleteCompanyPartnership(notificationId, appSessionService.requireCurrentEmail(session));
	}

	private RespondTicketTransferRequest withTransferSessionEmail(HttpSession session) {
		return new RespondTicketTransferRequest(appSessionService.requireCurrentEmail(session));
	}

	private RespondCompanyAccessRequest withCompanyAccessSessionEmail(HttpSession session) {
		return new RespondCompanyAccessRequest(appSessionService.requireCurrentEmail(session));
	}
}
