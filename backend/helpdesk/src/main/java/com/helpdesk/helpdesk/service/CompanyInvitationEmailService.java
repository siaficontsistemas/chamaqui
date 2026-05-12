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
public class CompanyInvitationEmailService {

	private static final Logger logger = LoggerFactory.getLogger(CompanyInvitationEmailService.class);

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final String fromAddress;
	private final String frontendBaseUrl;

	public CompanyInvitationEmailService(
		ObjectProvider<JavaMailSender> mailSenderProvider,
		@Value("${app.mail.company-invite.from:}") String fromAddress,
		@Value("${app.frontend.base-url:}") String frontendBaseUrl
	) {
		this.mailSenderProvider = mailSenderProvider;
		this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
		this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
	}

	public void sendInvitation(
		String recipientEmail,
		String recipientName,
		String companyName,
		String inviteToken
	) {
		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			throw new IllegalStateException("O envio do convite por email não está disponível no momento.");
		}
		if (fromAddress.isBlank()) {
			throw new IllegalStateException("O remetente do email de convite não está configurado.");
		}
		if (frontendBaseUrl.isBlank()) {
			throw new IllegalStateException("A URL pública do frontend não está configurada para montar o link do convite.");
		}

		String inviteUrl = frontendBaseUrl + "/register?companyInvite=" + inviteToken;

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(fromAddress);
			helper.setTo(recipientEmail);
			helper.setSubject("Convite para entrar na empresa " + companyName);
			helper.setText(
				buildPlainBody(recipientName, companyName, inviteUrl),
				buildHtmlBody(recipientName, companyName, inviteUrl)
			);
			mailSender.send(message);
		} catch (MessagingException | RuntimeException exception) {
			logger.error(
				"Falha ao enviar email de convite para {} usando remetente {} e frontend {}.",
				recipientEmail,
				fromAddress,
				frontendBaseUrl,
				exception
			);
			throw new IllegalStateException(
				"Não foi possível enviar o email de convite para a pessoa informada. Motivo técnico: "
					+ resolveRootCauseMessage(exception),
				exception
			);
		}
	}

	private String buildPlainBody(String recipientName, String companyName, String inviteUrl) {
		return """
			Olá %s,

			Você foi convidado(a) para participar da empresa %s no ChamAqui Helpdesk.

			Use o link abaixo para abrir o cadastro já preenchido e concluir sua entrada na empresa:
			%s
			""".formatted(recipientName, companyName, inviteUrl);
	}

	private String buildHtmlBody(String recipientName, String companyName, String inviteUrl) {
		return """
			<!DOCTYPE html>
			<html lang="pt-BR">
			<head>
			  <meta charset="UTF-8">
			  <meta name="viewport" content="width=device-width, initial-scale=1.0">
			  <title>Convite para empresa</title>
			</head>
			<body style="margin:0;padding:24px;background:#f3f6fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
			  <table role="presentation" style="width:100%%;border-collapse:collapse;">
			    <tr>
			      <td align="center">
			        <table role="presentation" style="width:100%%;max-width:640px;border-collapse:collapse;background:#ffffff;border:1px solid #dbe4f0;border-radius:20px;overflow:hidden;">
			          <tr>
			            <td style="padding:32px;background:linear-gradient(135deg,#1d4ed8,#2563eb);color:#ffffff;">
			              <div style="font-size:12px;letter-spacing:0.12em;text-transform:uppercase;opacity:0.88;">ChamAqui Helpdesk</div>
			              <h1 style="margin:12px 0 8px;font-size:28px;line-height:1.2;">Você foi convidado(a)</h1>
			              <p style="margin:0;font-size:15px;line-height:1.7;">Olá, <strong>%s</strong>. A empresa <strong>%s</strong> convidou você para participar da equipe.</p>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:28px 24px 32px;">
			              <p style="margin:0 0 18px;font-size:15px;line-height:1.7;color:#475569;">
			                Clique no botão abaixo para abrir o cadastro já preenchido e concluir sua entrada na empresa.
			              </p>
			              <a href="%s" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#1d4ed8;color:#ffffff;text-decoration:none;font-weight:700;">
			                Concluir cadastro
			              </a>
			              <p style="margin:20px 0 0;font-size:13px;line-height:1.6;color:#64748b;">
			                Se o botão não abrir, copie e cole este link no navegador:<br>
			                <span style="word-break:break-all;">%s</span>
			              </p>
			            </td>
			          </tr>
			        </table>
			      </td>
			    </tr>
			  </table>
			</body>
			</html>
			""".formatted(escapeHtml(recipientName), escapeHtml(companyName), inviteUrl, inviteUrl);
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

	private String resolveRootCauseMessage(Throwable throwable) {
		Throwable current = throwable;

		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}

		String message = current.getMessage();
		if (message == null || message.isBlank()) {
			return "erro desconhecido no provedor de email";
		}

		return message.trim();
	}
}
