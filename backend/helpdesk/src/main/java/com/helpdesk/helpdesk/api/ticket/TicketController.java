package com.helpdesk.helpdesk.api.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.helpdesk.helpdesk.api.publicapi.PublicTicketAttachmentController;
import com.helpdesk.helpdesk.dto.ticket.CloseTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketMessageRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.DeleteTicketsRequest;
import com.helpdesk.helpdesk.dto.ticket.RequestTicketTransferRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketMessageResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTransferCandidateResponse;
import com.helpdesk.helpdesk.dto.ticket.UpdateTicketTitleRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.TicketService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

	private final TicketService ticketService;
	private final AppSessionService appSessionService;

	public TicketController(TicketService ticketService, AppSessionService appSessionService) {
		this.ticketService = ticketService;
		this.appSessionService = appSessionService;
	}

	@GetMapping
	public List<TicketResponse> list(
		@RequestParam(required = false) String status,
		HttpSession session
	) {
		return ticketService.list(appSessionService.requireCurrentEmail(session), status);
	}

	@GetMapping("/summary")
	public TicketSummaryResponse summary(HttpSession session) {
		return ticketService.summary(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/{ticketId}")
	public TicketResponse get(@PathVariable UUID ticketId, HttpSession session) {
		return ticketService.get(ticketId, appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/{ticketId}/messages")
	public List<TicketMessageResponse> listMessages(
		@PathVariable UUID ticketId,
		HttpSession session
	) {
		return ticketService.listMessages(ticketId, appSessionService.requireCurrentEmail(session));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@Valid @RequestBody CreateTicketRequest request, HttpSession session) {
		return ticketService.create(withRequesterEmail(request, session), List.of());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse createWithAttachments(
		@Valid @RequestPart("payload") CreateTicketRequest request,
		@RequestPart(name = "files", required = false) List<MultipartFile> files,
		HttpSession session
	) {
		return ticketService.create(withRequesterEmail(request, session), files);
	}

	@PostMapping("/{ticketId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketMessageResponse addMessage(
		@PathVariable UUID ticketId,
		@Valid @RequestBody CreateTicketMessageRequest request,
		HttpSession session
	) {
		return ticketService.addMessage(ticketId, withAuthorEmail(request, session), List.of());
	}

	@PostMapping(path = "/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TicketMessageResponse addMessageWithAttachments(
		@PathVariable UUID ticketId,
		@Valid @RequestPart("payload") CreateTicketMessageRequest request,
		@RequestPart(name = "files", required = false) List<MultipartFile> files,
		HttpSession session
	) {
		return ticketService.addMessage(ticketId, withAuthorEmail(request, session), files);
	}

	@PutMapping("/{ticketId}/title")
	public TicketResponse updateTitle(
		@PathVariable UUID ticketId,
		@Valid @RequestBody UpdateTicketTitleRequest request,
		HttpSession session
	) {
		return ticketService.updateTitle(ticketId, withAuthorEmail(request, session));
	}

	@GetMapping("/{ticketId}/attachments/{attachmentId}")
	public ResponseEntity<Resource> downloadAttachment(
		@PathVariable UUID ticketId,
		@PathVariable UUID attachmentId,
		HttpSession session
	) {
		return PublicTicketAttachmentController.buildAttachmentResponse(ticketService.downloadAttachment(
			ticketId,
			attachmentId,
			appSessionService.requireCurrentEmail(session)
		));
	}

	@PostMapping("/{ticketId}/close")
	public TicketResponse closeTicket(
		@PathVariable UUID ticketId,
		@Valid @RequestBody CloseTicketRequest request,
		HttpSession session
	) {
		return ticketService.closeTicket(ticketId, withAuthorEmail(session));
	}

	@PostMapping("/delete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTickets(@Valid @RequestBody DeleteTicketsRequest request, HttpSession session) {
		ticketService.deleteTickets(withAuthorEmail(request, session));
	}

	@GetMapping("/{ticketId}/transfer-candidates")
	public List<TicketTransferCandidateResponse> listTransferCandidates(
		@PathVariable UUID ticketId,
		HttpSession session
	) {
		return ticketService.listTransferCandidates(ticketId, appSessionService.requireCurrentEmail(session));
	}

	@PostMapping("/{ticketId}/transfer")
	public TicketResponse requestTransfer(
		@PathVariable UUID ticketId,
		@Valid @RequestBody RequestTicketTransferRequest request,
		HttpSession session
	) {
		return ticketService.requestTransfer(ticketId, withAuthorEmail(request, session));
	}

	private CreateTicketRequest withRequesterEmail(CreateTicketRequest request, HttpSession session) {
		return new CreateTicketRequest(
			request.description(),
			request.companyOwnerId(),
			request.sectorId(),
			request.assignedToUserId(),
			request.priorityCode(),
			appSessionService.requireCurrentEmail(session),
			request.copyEmail()
		);
	}

	private CreateTicketMessageRequest withAuthorEmail(CreateTicketMessageRequest request, HttpSession session) {
		return new CreateTicketMessageRequest(appSessionService.requireCurrentEmail(session), request.message());
	}

	private UpdateTicketTitleRequest withAuthorEmail(UpdateTicketTitleRequest request, HttpSession session) {
		return new UpdateTicketTitleRequest(request.title(), appSessionService.requireCurrentEmail(session));
	}

	private CloseTicketRequest withAuthorEmail(HttpSession session) {
		return new CloseTicketRequest(appSessionService.requireCurrentEmail(session));
	}

	private DeleteTicketsRequest withAuthorEmail(DeleteTicketsRequest request, HttpSession session) {
		return new DeleteTicketsRequest(request.ticketIds(), appSessionService.requireCurrentEmail(session));
	}

	private RequestTicketTransferRequest withAuthorEmail(
		RequestTicketTransferRequest request,
		HttpSession session
	) {
		return new RequestTicketTransferRequest(appSessionService.requireCurrentEmail(session), request.recipientUserId());
	}
}
