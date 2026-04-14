package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketAttachment;
import com.helpdesk.helpdesk.domain.TicketMessage;

@Service
public class TicketClosureEmailService {

	private static final Logger logger = LoggerFactory.getLogger(TicketClosureEmailService.class);

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final String fromAddress;
	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"));

	public TicketClosureEmailService(
		ObjectProvider<JavaMailSender> mailSenderProvider,
		@Value("${app.mail.ticket-closure.from:}") String fromAddress
	) {
		this.mailSenderProvider = mailSenderProvider;
		this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
	}

	public void sendConversationTranscript(
		Ticket ticket,
		List<TicketMessage> messages,
		Map<UUID, List<TicketAttachment>> attachmentsByMessageId
	) {
		String recipient = normalizeOptionalEmail(ticket.getCopyEmail());

		if (recipient == null) {
			return;
		}

		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			logger.warn("Envio do historico do chamado {} ignorado: JavaMailSender indisponivel.", ticket.getProtocol());
			return;
		}

		if (fromAddress.isBlank()) {
			logger.warn("Envio do historico do chamado {} ignorado: remetente nao configurado.", ticket.getProtocol());
			return;
		}

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(fromAddress);
			helper.setTo(recipient);
			helper.setSubject("Chamado encerrado: " + ticket.getProtocol() + " - " + ticket.getTitle());
			helper.setText(
				buildPlainBody(ticket, messages, attachmentsByMessageId),
				buildHtmlBody(ticket, messages, attachmentsByMessageId)
			);
			mailSender.send(message);
		} catch (MessagingException | RuntimeException exception) {
			logger.warn(
				"Falha ao enviar historico do chamado {} para {}.",
				ticket.getProtocol(),
				recipient,
				exception
			);
		}
	}

	private String buildPlainBody(
		Ticket ticket,
		List<TicketMessage> messages,
		Map<UUID, List<TicketAttachment>> attachmentsByMessageId
	) {
		StringBuilder body = new StringBuilder();
		body.append("O chamado abaixo foi encerrado e segue o historico completo da conversa.")
			.append("\n\n")
			.append("Protocolo: ").append(ticket.getProtocol()).append('\n')
			.append("Assunto: ").append(ticket.getTitle()).append('\n')
			.append("Setor: ").append(ticket.getSector().getName()).append('\n')
			.append("Solicitante: ").append(ticket.getRequester().getFullName())
			.append(" <").append(ticket.getRequester().getEmail()).append(">").append('\n')
			.append("Responsavel: ").append(ticket.getAssignedTo() == null ? "Nao informado" : ticket.getAssignedTo().getFullName())
			.append('\n')
			.append("Aberto em: ").append(formatDateTime(ticket.getOpenedAt())).append('\n')
			.append("Encerrado em: ").append(formatDateTime(ticket.getClosedAt())).append('\n')
			.append('\n')
			.append("Conversa")
			.append('\n')
			.append("========")
			.append('\n');

		for (TicketMessage ticketMessage : messages) {
			body.append('\n')
				.append(formatDateTime(ticketMessage.getCreatedAt()))
				.append(" - ")
				.append(ticketMessage.getAuthor().getFullName())
				.append(" <")
				.append(ticketMessage.getAuthor().getEmail())
				.append('>')
				.append('\n')
				.append(ticketMessage.getMessage())
				.append('\n');

			List<TicketAttachment> attachments = attachmentsByMessageId.get(ticketMessage.getId());
			if (attachments != null && !attachments.isEmpty()) {
				body.append("Anexos:")
					.append('\n');
				for (TicketAttachment attachment : attachments) {
					body.append("- ")
						.append(attachment.getOriginalFileName())
						.append(" (")
						.append(formatFileSize(attachment.getSizeBytes()))
						.append(')')
						.append('\n');
				}
			}
		}

		return body.toString();
	}

	private String buildHtmlBody(
		Ticket ticket,
		List<TicketMessage> messages,
		Map<UUID, List<TicketAttachment>> attachmentsByMessageId
	) {
		StringBuilder body = new StringBuilder();
		body.append("""
			<!DOCTYPE html>
			<html lang="pt-BR">
			<head>
			  <meta charset="UTF-8">
			  <meta name="viewport" content="width=device-width, initial-scale=1.0">
			  <title>Chamado encerrado</title>
			</head>
			<body style="margin:0;padding:24px;background-color:#f3f6fb;font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;color:#1f2937;">
			  <table role="presentation" style="width:100%;border-collapse:collapse;">
			    <tr>
			      <td align="center">
			        <table role="presentation" style="width:100%;max-width:760px;border-collapse:collapse;background:#ffffff;border:1px solid #dbe4f0;border-radius:20px;overflow:hidden;">
			          <tr>
			            <td style="padding:32px;background:linear-gradient(135deg,#1d4ed8,#2563eb);color:#ffffff;">
			              <div style="font-size:12px;letter-spacing:0.12em;text-transform:uppercase;opacity:0.88;">Central de Chamados</div>
			              <h1 style="margin:12px 0 8px;font-size:28px;line-height:1.2;">Chamado encerrado</h1>
			              <p style="margin:0;font-size:15px;line-height:1.6;opacity:0.95;">O chamado <strong>"""
			)
			.append(escapeHtml(ticket.getProtocol()))
			.append("</strong> foi finalizado. Abaixo esta o historico completo da conversa.</p>")
			.append("""
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:24px 24px 8px;">
			              <table role="presentation" style="width:100%;border-collapse:separate;border-spacing:12px;">
			                <tr>
			                  <td style="width:50%;padding:18px;border:1px solid #e5edf6;border-radius:16px;background:#f8fbff;vertical-align:top;">
			                    <div style="font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;">Protocolo</div>
			                    <div style="margin-top:6px;font-size:18px;font-weight:700;color:#0f172a;">"""
			)
			.append(escapeHtml(ticket.getProtocol()))
			.append("</div></td>")
			.append("""
			                  <td style="width:50%;padding:18px;border:1px solid #e5edf6;border-radius:16px;background:#f8fbff;vertical-align:top;">
			                    <div style="font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;">Assunto</div>
			                    <div style="margin-top:6px;font-size:18px;font-weight:700;color:#0f172a;">"""
			)
			.append(escapeHtml(ticket.getTitle()))
			.append("</div></td>")
			.append("""
			                </tr>
			                <tr>
			                  <td style="padding:18px;border:1px solid #e5edf6;border-radius:16px;background:#f8fbff;vertical-align:top;">
			                    <div style="font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;">Setor</div>
			                    <div style="margin-top:6px;font-size:16px;font-weight:600;color:#0f172a;">"""
			)
			.append(escapeHtml(ticket.getSector().getName()))
			.append("</div></td>")
			.append("""
			                  <td style="padding:18px;border:1px solid #e5edf6;border-radius:16px;background:#f8fbff;vertical-align:top;">
			                    <div style="font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;">Responsavel</div>
			                    <div style="margin-top:6px;font-size:16px;font-weight:600;color:#0f172a;">"""
			)
			.append(escapeHtml(ticket.getAssignedTo() == null ? "Nao informado" : ticket.getAssignedTo().getFullName()))
			.append("</div></td>")
			.append("""
			                </tr>
			              </table>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:8px 24px 0;">
			              <div style="padding:20px;border:1px solid #e5edf6;border-radius:18px;background:#ffffff;">
			                <div style="font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;margin-bottom:14px;">Detalhes</div>
			                <div style="font-size:15px;line-height:1.8;color:#334155;">
			                  <strong>Solicitante:</strong> """
			)
			.append(escapeHtml(formatPerson(ticket.getRequester().getFullName(), ticket.getRequester().getEmail())))
			.append("<br><strong>Aberto em:</strong> ")
			.append(escapeHtml(formatDateTime(ticket.getOpenedAt())))
			.append("<br><strong>Encerrado em:</strong> ")
			.append(escapeHtml(formatDateTime(ticket.getClosedAt())))
			.append("""
			                </div>
			              </div>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:28px 24px 12px;">
			              <h2 style="margin:0;font-size:20px;color:#0f172a;">Historico da conversa</h2>
			              <p style="margin:8px 0 0;font-size:14px;line-height:1.6;color:#64748b;">Todas as mensagens trocadas durante o atendimento estao listadas abaixo.</p>
			            </td>
			          </tr>
			""");

		if (messages.isEmpty()) {
			body.append("""
			          <tr>
			            <td style="padding:0 24px 24px;">
			              <div style="padding:24px;border:1px dashed #cbd5e1;border-radius:18px;background:#f8fafc;color:#64748b;font-size:14px;text-align:center;">
			                Nenhuma mensagem foi registrada na conversa.
			              </div>
			            </td>
			          </tr>
			""");
		}

		for (TicketMessage ticketMessage : messages) {
			body.append("""
			          <tr>
			            <td style="padding:0 24px 16px;">
			              <div style="border:1px solid #e5edf6;border-radius:18px;background:#ffffff;overflow:hidden;">
			                <div style="padding:16px 20px;background:#f8fbff;border-bottom:1px solid #e5edf6;">
			                  <div style="font-size:16px;font-weight:700;color:#0f172a;">"""
			)
			.append(escapeHtml(ticketMessage.getAuthor().getFullName()))
			.append("</div><div style=\"margin-top:4px;font-size:13px;color:#64748b;\">")
			.append(escapeHtml(ticketMessage.getAuthor().getEmail()))
			.append(" • ")
			.append(escapeHtml(formatDateTime(ticketMessage.getCreatedAt())))
			.append("</div></div><div style=\"padding:20px;font-size:15px;line-height:1.7;color:#334155;white-space:normal;\">")
			.append(formatMessageHtml(ticketMessage.getMessage()))
			.append("</div>");

			List<TicketAttachment> attachments = attachmentsByMessageId.get(ticketMessage.getId());
			if (attachments != null && !attachments.isEmpty()) {
				body.append("""
			                <div style="padding:0 20px 20px;">
			                  <div style="margin-bottom:10px;font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;">Anexos</div>
			                  <table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;">
			""");

				for (TicketAttachment attachment : attachments) {
					body.append("""
			                    <tr>
			                      <td style="padding:12px 14px;border:1px solid #e5edf6;border-radius:12px;background:#f8fafc;font-size:14px;color:#334155;">
			                        <strong>"""
					)
					.append(escapeHtml(attachment.getOriginalFileName()))
					.append("</strong><br><span style=\"color:#64748b;\">")
					.append(escapeHtml(formatFileSize(attachment.getSizeBytes())))
					.append("</span></td></tr>");
				}

				body.append("""
			                  </table>
			                </div>
			""");
			}

			body.append("""
			              </div>
			            </td>
			          </tr>
			""");
		}

		body.append("""
			          <tr>
			            <td style="padding:12px 24px 32px;">
			              <div style="padding:18px 20px;border-radius:16px;background:#eff6ff;color:#1e3a8a;font-size:13px;line-height:1.7;">
			                Este e-mail foi enviado automaticamente no encerramento do chamado para manter o historico acessivel.
			              </div>
			            </td>
			          </tr>
			        </table>
			      </td>
			    </tr>
			  </table>
			</body>
			</html>
			""");

		return body.toString();
	}

	private String formatDateTime(OffsetDateTime value) {
		if (value == null) {
			return "Nao informado";
		}

		return dateTimeFormatter.format(value);
	}

	private String formatFileSize(long sizeBytes) {
		if (sizeBytes < 1024) {
			return sizeBytes + " B";
		}
		if (sizeBytes < 1024 * 1024) {
			return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0d);
		}

		return String.format(Locale.ROOT, "%.1f MB", sizeBytes / (1024.0d * 1024.0d));
	}

	private String normalizeOptionalEmail(String email) {
		if (email == null) {
			return null;
		}

		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		return normalizedEmail.isBlank() ? null : normalizedEmail;
	}

	private String formatPerson(String fullName, String email) {
		return fullName + " <" + email + ">";
	}

	private String formatMessageHtml(String message) {
		if (message == null || message.isBlank()) {
			return "<em style=\"color:#64748b;\">Mensagem sem conteudo.</em>";
		}

		return escapeHtml(message).replace("\n", "<br>");
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
