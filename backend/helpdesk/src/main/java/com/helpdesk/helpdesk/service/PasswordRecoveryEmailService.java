package com.helpdesk.helpdesk.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class PasswordRecoveryEmailService {

	private static final Logger logger = LoggerFactory.getLogger(PasswordRecoveryEmailService.class);

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final TenantAccessService tenantAccessService;
	private final FrontendPublicUrlService frontendPublicUrlService;
	private final String fromAddress;

	public PasswordRecoveryEmailService(
		ObjectProvider<JavaMailSender> mailSenderProvider,
		TenantAccessService tenantAccessService,
		FrontendPublicUrlService frontendPublicUrlService,
		@Value("${app.mail.password-recovery.from:${app.mail.from:}}") String fromAddress
	) {
		this.mailSenderProvider = mailSenderProvider;
		this.tenantAccessService = tenantAccessService;
		this.frontendPublicUrlService = frontendPublicUrlService;
		this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
	}

	public void sendResetPasswordEmail(String recipientEmail, String recipientName, String resetToken) {
		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			throw new IllegalStateException("O envio de recuperação de senha por email não está disponível no momento.");
		}
		if (fromAddress.isBlank()) {
			throw new IllegalStateException("O remetente do email de recuperação de senha não está configurado.");
		}
		if (!frontendPublicUrlService.isConfigured()) {
			throw new IllegalStateException("A URL pública do frontend não está configurada para redefinir a senha.");
		}

		String resetUrl = buildResetUrl(resetToken);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(fromAddress);
			helper.setTo(recipientEmail);
			helper.setSubject("Redefinição de senha - ChamAqui Helpdesk");
			helper.setText(
				buildPlainBody(recipientName, resetUrl),
				buildHtmlBody(recipientName, resetUrl)
			);
			mailSender.send(message);
		} catch (MessagingException | RuntimeException exception) {
			logger.error(
				"Falha ao enviar email de redefinição de senha para {}.",
				recipientEmail,
				exception
			);
			throw new IllegalStateException(
				"Não foi possível enviar o email de recuperação de senha no momento.",
				exception
			);
		}
	}

	private String buildResetUrl(String resetToken) {
		String subdomain = tenantAccessService.getCurrentTenant()
			.map(tenant -> tenant.subdomain())
			.orElse(null);
		return frontendPublicUrlService.buildUrl(
			subdomain,
			"/login",
			java.util.Map.of("resetPasswordToken", resetToken)
		);
	}

	private String buildPlainBody(String recipientName, String resetUrl) {
		return """
			Olá %s,

			Recebemos uma solicitação para redefinir a senha da sua conta no ChamAqui Helpdesk.

			Use o link abaixo para cadastrar uma nova senha:
			%s

			Se você não solicitou essa alteração, pode ignorar este email.
			""".formatted(resolveName(recipientName), resetUrl);
	}

	private String buildHtmlBody(String recipientName, String resetUrl) {
		return """
			<!DOCTYPE html>
			<html lang="pt-BR">
			<head>
			  <meta charset="UTF-8">
			  <meta name="viewport" content="width=device-width, initial-scale=1.0">
			  <title>Redefinição de senha</title>
			</head>
			<body style="margin:0;padding:24px;background:#f3f6fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
			  <table role="presentation" style="width:100%%;border-collapse:collapse;">
			    <tr>
			      <td align="center">
			        <table role="presentation" style="width:100%%;max-width:640px;border-collapse:collapse;background:#ffffff;border:1px solid #dbe4f0;border-radius:20px;overflow:hidden;">
			          <tr>
			            <td style="padding:32px;background:linear-gradient(135deg,#1d4ed8,#2563eb);color:#ffffff;">
			              <div style="font-size:12px;letter-spacing:0.12em;text-transform:uppercase;opacity:0.88;">ChamAqui Helpdesk</div>
			              <h1 style="margin:12px 0 8px;font-size:28px;line-height:1.2;">Redefinição de senha</h1>
			              <p style="margin:0;font-size:15px;line-height:1.7;">Olá, <strong>%s</strong>. Recebemos uma solicitação para trocar a senha da sua conta.</p>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:28px 24px 32px;">
			              <p style="margin:0 0 18px;font-size:15px;line-height:1.7;color:#475569;">
			                Clique no botão abaixo para cadastrar uma nova senha.
			              </p>
			              <a href="%s" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#1d4ed8;color:#ffffff;text-decoration:none;font-weight:700;">
			                Redefinir senha
			              </a>
			              <p style="margin:20px 0 0;font-size:13px;line-height:1.6;color:#64748b;">
			                Se o botão não abrir, copie e cole este link no navegador:<br>
			                <span style="word-break:break-all;">%s</span>
			              </p>
			              <p style="margin:18px 0 0;font-size:13px;line-height:1.6;color:#64748b;">
			                Se você não solicitou essa alteração, ignore este email.
			              </p>
			            </td>
			          </tr>
			        </table>
			      </td>
			    </tr>
			  </table>
			</body>
			</html>
			""".formatted(escapeHtml(resolveName(recipientName)), resetUrl, resetUrl);
	}

	private String resolveName(String value) {
		if (value == null || value.isBlank()) {
			return "usuário";
		}
		return value.trim();
	}

	private String escapeHtml(String value) {
		if (value == null) {
			return "";
		}

		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}
}
