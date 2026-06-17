package com.helpdesk.helpdesk.api.whatsapp;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.whatsapp.SendWhatsappTestMessageRequest;
import com.helpdesk.helpdesk.dto.whatsapp.StartWhatsappSessionRequest;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappOperationResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappQrCodeResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappSessionStatusResponse;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.WhatsappService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsappController {

	private final WhatsappService whatsappService;
	private final AppSessionService appSessionService;

	public WhatsappController(WhatsappService whatsappService, AppSessionService appSessionService) {
		this.whatsappService = whatsappService;
		this.appSessionService = appSessionService;
	}

	@PostMapping("/session/start")
	public WhatsappSessionStatusResponse startSession(
		@Valid @RequestBody(required = false) StartWhatsappSessionRequest request,
		HttpSession session
	) {
		StartWhatsappSessionRequest effectiveRequest = new StartWhatsappSessionRequest(
			appSessionService.requireCurrentEmail(session),
			request == null ? null : request.webhook(),
			request == null ? null : request.waitQrCode()
		);
		return whatsappService.startSession(effectiveRequest);
	}

	@GetMapping("/session/status")
	public WhatsappSessionStatusResponse getSessionStatus(HttpSession session) {
		return whatsappService.getSessionStatus(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/session/qrcode")
	public WhatsappQrCodeResponse getQrCode(HttpSession session) {
		return whatsappService.getQrCode(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping(value = "/session/qrcode/image", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> getQrCodeImage(HttpSession session) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(MediaType.IMAGE_PNG)
			.body(whatsappService.getQrCodeImageBytes(appSessionService.requireCurrentEmail(session)));
	}

	@GetMapping(value = "/session/qrcode/view", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> getQrCodeView(HttpSession session) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(MediaType.TEXT_HTML)
			.body(whatsappService.getQrCodeHtmlView(appSessionService.requireCurrentEmail(session)));
	}

	@PostMapping("/messages/test")
	public WhatsappOperationResponse sendTestMessage(
		@Valid @RequestBody SendWhatsappTestMessageRequest request,
		HttpSession session
	) {
		return whatsappService.sendTestMessage(
			new SendWhatsappTestMessageRequest(
				appSessionService.requireCurrentEmail(session),
				request.phone(),
				request.message()
			)
		);
	}
}
