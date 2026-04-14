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

import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
import com.helpdesk.helpdesk.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
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
}
