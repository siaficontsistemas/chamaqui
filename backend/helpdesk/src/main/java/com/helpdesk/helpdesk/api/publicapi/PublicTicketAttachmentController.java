package com.helpdesk.helpdesk.api.publicapi;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.service.TicketService;

@RestController
@RequestMapping("/api/v1/public/tickets")
public class PublicTicketAttachmentController {

	private final TicketService ticketService;

	public PublicTicketAttachmentController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping("/{ticketId}/attachments/{attachmentId}")
	public ResponseEntity<Resource> downloadAttachment(
		@PathVariable UUID ticketId,
		@PathVariable UUID attachmentId
	) {
		return buildAttachmentResponse(ticketService.downloadPublicAttachment(ticketId, attachmentId));
	}

	static ResponseEntity<Resource> buildAttachmentResponse(TicketService.AttachmentDownload attachment) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(attachment.contentType()))
			.contentLength(attachment.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(attachment).toString())
			.body(attachment.resource());
	}

	private static ContentDisposition buildContentDisposition(TicketService.AttachmentDownload attachment) {
		ContentDisposition.Builder builder = supportsInlinePreview(attachment.contentType())
			? ContentDisposition.inline()
			: ContentDisposition.attachment();
		return builder.filename(attachment.originalFileName(), StandardCharsets.UTF_8).build();
	}

	private static boolean supportsInlinePreview(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return false;
		}
		return contentType.startsWith("image/")
			|| contentType.startsWith("audio/")
			|| contentType.startsWith("video/")
			|| contentType.startsWith("text/")
			|| "application/pdf".equalsIgnoreCase(contentType);
	}
}
