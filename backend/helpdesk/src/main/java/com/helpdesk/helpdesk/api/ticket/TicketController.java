package com.helpdesk.helpdesk.api.ticket;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import com.helpdesk.helpdesk.dto.ticket.CloseTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketMessageRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.DeleteTicketsRequest;
import com.helpdesk.helpdesk.dto.ticket.RequestTicketTransferRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketMessageResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTransferCandidateResponse;
import com.helpdesk.helpdesk.dto.ticket.UpdateTicketClassificationRequest;
import com.helpdesk.helpdesk.dto.ticket.UpdateTicketTitleRequest;
import com.helpdesk.helpdesk.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketResponse> list(
		@RequestParam String email,
		@RequestParam(required = false) String status
	) {
		return ticketService.list(email, status);
	}

	@GetMapping("/summary")
	public TicketSummaryResponse summary(@RequestParam String email) {
		return ticketService.summary(email);
	}

	@GetMapping("/{ticketId}")
	public TicketResponse get(@PathVariable UUID ticketId, @RequestParam String email) {
		return ticketService.get(ticketId, email);
	}

	@GetMapping("/{ticketId}/messages")
	public List<TicketMessageResponse> listMessages(
		@PathVariable UUID ticketId,
		@RequestParam String email
	) {
		return ticketService.listMessages(ticketId, email);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
		return ticketService.create(request, List.of());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse createWithAttachments(
		@Valid @RequestPart("payload") CreateTicketRequest request,
		@RequestPart(name = "files", required = false) List<MultipartFile> files
	) {
		return ticketService.create(request, files);
	}

	@PostMapping("/{ticketId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketMessageResponse addMessage(
		@PathVariable UUID ticketId,
		@Valid @RequestBody CreateTicketMessageRequest request
	) {
		return ticketService.addMessage(ticketId, request, List.of());
	}

	@PostMapping(path = "/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TicketMessageResponse addMessageWithAttachments(
		@PathVariable UUID ticketId,
		@Valid @RequestPart("payload") CreateTicketMessageRequest request,
		@RequestPart(name = "files", required = false) List<MultipartFile> files
	) {
		return ticketService.addMessage(ticketId, request, files);
	}

	@PutMapping("/{ticketId}/title")
	public TicketResponse updateTitle(
		@PathVariable UUID ticketId,
		@Valid @RequestBody UpdateTicketTitleRequest request
	) {
		return ticketService.updateTitle(ticketId, request);
	}

	@PutMapping("/{ticketId}/classification")
	public TicketResponse updateClassification(
		@PathVariable UUID ticketId,
		@Valid @RequestBody UpdateTicketClassificationRequest request
	) {
		return ticketService.updateClassification(ticketId, request);
	}

	@GetMapping("/{ticketId}/attachments/{attachmentId}")
	public ResponseEntity<Resource> downloadAttachment(
		@PathVariable UUID ticketId,
		@PathVariable UUID attachmentId,
		@RequestParam String email
	) {
		TicketService.AttachmentDownload attachment = ticketService.downloadAttachment(
			ticketId,
			attachmentId,
			email
		);

		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(attachment.contentType()))
			.contentLength(attachment.sizeBytes())
			.header(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment()
					.filename(attachment.originalFileName(), StandardCharsets.UTF_8)
					.build()
					.toString()
			)
			.body(attachment.resource());
	}

	@PostMapping("/{ticketId}/close")
	public TicketResponse closeTicket(
		@PathVariable UUID ticketId,
		@Valid @RequestBody CloseTicketRequest request
	) {
		return ticketService.closeTicket(ticketId, request);
	}

	@PostMapping("/delete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTickets(@Valid @RequestBody DeleteTicketsRequest request) {
		ticketService.deleteTickets(request);
	}

	@GetMapping("/{ticketId}/transfer-candidates")
	public List<TicketTransferCandidateResponse> listTransferCandidates(
		@PathVariable UUID ticketId,
		@RequestParam String email
	) {
		return ticketService.listTransferCandidates(ticketId, email);
	}

	@PostMapping("/{ticketId}/transfer")
	public TicketResponse requestTransfer(
		@PathVariable UUID ticketId,
		@Valid @RequestBody RequestTicketTransferRequest request
	) {
		return ticketService.requestTransfer(ticketId, request);
	}
}
