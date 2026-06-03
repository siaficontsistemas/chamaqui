package com.helpdesk.helpdesk.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "calendar_reminder_notifications")
public class CalendarReminderNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "obligation_id", nullable = false)
	private CalendarObligation obligation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private User recipient;

	@Column(nullable = false)
	private boolean hidden;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "whatsapp_sent_at")
	private OffsetDateTime whatsappSentAt;

	@SuppressWarnings("unused")
	@PrePersist
	void onCreate() {
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public CalendarObligation getObligation() {
		return obligation;
	}

	public void setObligation(CalendarObligation obligation) {
		this.obligation = obligation;
	}

	public User getRecipient() {
		return recipient;
	}

	public void setRecipient(User recipient) {
		this.recipient = recipient;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getWhatsappSentAt() {
		return whatsappSentAt;
	}

	public void setWhatsappSentAt(OffsetDateTime whatsappSentAt) {
		this.whatsappSentAt = whatsappSentAt;
	}
}
