package com.helpdesk.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class TicketAttachmentStorageServiceTest {

	@TempDir
	java.nio.file.Path tempDirectory;

	@Test
	void shouldStoreCommonVideoFormatsAndPreserveTheirContentType() throws Exception {
		TicketAttachmentStorageService service = new TicketAttachmentStorageService(
			tempDirectory.toString(),
			"",
			100 * 1024 * 1024
		);

		var mp4 = service.store(new MockMultipartFile("files", "recording.mp4", "application/octet-stream", new byte[] { 1 }));
		var mov = service.store(new MockMultipartFile("files", "recording.mov", "video/quicktime", new byte[] { 2 }));

		assertEquals("video/mp4", mp4.contentType());
		assertEquals("video/quicktime", mov.contentType());
		assertEquals(1, Files.size(tempDirectory.resolve(mp4.storageKey())));
		assertEquals(1, Files.size(tempDirectory.resolve(mov.storageKey())));
	}

	@Test
	void shouldAcceptVideoMimeTypesNotKnownByTheExtensionMap() {
		TicketAttachmentStorageService service = new TicketAttachmentStorageService(
			tempDirectory.toString(),
			"",
			100 * 1024 * 1024
		);

		assertDoesNotThrow(() -> service.store(
			new MockMultipartFile("files", "recording.xyz", "video/x-custom", new byte[] { 1 })
		));
	}

	@Test
	void shouldStillRejectNonVideoMimeTypes() {
		TicketAttachmentStorageService service = new TicketAttachmentStorageService(
			tempDirectory.toString(),
			"",
			100 * 1024 * 1024
		);

		assertThrows(IllegalArgumentException.class, () -> service.store(
			new MockMultipartFile("files", "malware.bin", "application/x-malware", new byte[] { 1 })
		));
	}
}
