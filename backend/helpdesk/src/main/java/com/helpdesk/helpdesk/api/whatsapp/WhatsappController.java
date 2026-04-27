package com.helpdesk.helpdesk.api.whatsapp;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.whatsapp.SendWhatsappTestMessageRequest;
import com.helpdesk.helpdesk.dto.whatsapp.StartWhatsappSessionRequest;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappOperationResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappQrCodeResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappSessionStatusResponse;
import com.helpdesk.helpdesk.service.WhatsappService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsappController {

	private final WhatsappService whatsappService;

	public WhatsappController(WhatsappService whatsappService) {
		this.whatsappService = whatsappService;
	}

	@PostMapping("/session/start")
	public WhatsappSessionStatusResponse startSession(@Valid @RequestBody(required = false) StartWhatsappSessionRequest request) {
		return whatsappService.startSession(request);
	}

	@GetMapping("/session/status")
	public WhatsappSessionStatusResponse getSessionStatus(@RequestParam String adminEmail) {
		return whatsappService.getSessionStatus(adminEmail);
	}

	@GetMapping("/session/qrcode")
	public WhatsappQrCodeResponse getQrCode(@RequestParam String adminEmail) {
		return whatsappService.getQrCode(adminEmail);
	}

	@GetMapping(value = "/session/qrcode/image", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> getQrCodeImage(@RequestParam String adminEmail) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(MediaType.IMAGE_PNG)
			.body(whatsappService.getQrCodeImageBytes(adminEmail));
	}

	@GetMapping(value = "/session/qrcode/view", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> getQrCodeView(@RequestParam String adminEmail) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(MediaType.TEXT_HTML)
			.body(whatsappService.getQrCodeHtmlView(adminEmail));
	}

	@PostMapping("/messages/test")
	public WhatsappOperationResponse sendTestMessage(@Valid @RequestBody SendWhatsappTestMessageRequest request) {
		return whatsappService.sendTestMessage(request);
	}
}
