package com.helpdesk.helpdesk.api.publicapi;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.helpdesk.helpdesk.service.TicketService;

@ExtendWith(MockitoExtension.class)
class PublicTicketAttachmentControllerTest {

	private MockMvc mockMvc;

	@Mock
	private TicketService ticketService;

	@InjectMocks
	private PublicTicketAttachmentController publicTicketAttachmentController;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(publicTicketAttachmentController).build();
	}

	@Test
	void shouldReturnInlineDispositionForPreviewableAttachments() throws Exception {
		UUID ticketId = UUID.randomUUID();
		UUID attachmentId = UUID.randomUUID();

		when(ticketService.downloadPublicAttachment(ticketId, attachmentId)).thenReturn(
			new TicketService.AttachmentDownload(
				new ByteArrayResource("conteudo".getBytes(StandardCharsets.UTF_8)),
				"captura.png",
				"image/png",
				8
			)
		);

		mockMvc.perform(get("/api/v1/public/tickets/{ticketId}/attachments/{attachmentId}", ticketId, attachmentId))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
			.andExpect(header().string("Content-Type", "image/png"));
	}

	@Test
	void shouldReturnInlineDispositionForVideoAttachments() throws Exception {
		UUID ticketId = UUID.randomUUID();
		UUID attachmentId = UUID.randomUUID();

		when(ticketService.downloadPublicAttachment(ticketId, attachmentId)).thenReturn(
			new TicketService.AttachmentDownload(
				new ByteArrayResource(new byte[] { 0, 1, 2 }),
				"video.mp4",
				"video/mp4",
				3
			)
		);

		mockMvc.perform(get("/api/v1/public/tickets/{ticketId}/attachments/{attachmentId}", ticketId, attachmentId))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
			.andExpect(header().string("Content-Type", "video/mp4"));
	}
}
