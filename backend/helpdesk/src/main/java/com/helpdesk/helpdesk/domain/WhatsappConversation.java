package com.helpdesk.helpdesk.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "whatsapp_conversations",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_whatsapp_conversations_company_phone",
			columnNames = {"company_owner_id", "phone_number"}
		)
	}
)
public class WhatsappConversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_owner_id")
	private User companyOwner;

	@Column(name = "phone_number", nullable = false, length = 30)
	private String phoneNumber;

	@Column(name = "whatsapp_transport_id", length = 80)
	private String whatsappTransportId;

	@Column(name = "normal_conversation_active", nullable = false)
	private boolean normalConversationActive;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_step", nullable = false, length = 40)
	private WhatsappConversationStep currentStep = WhatsappConversationStep.ASK_INITIAL_MODE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sector_id")
	private Sector sector;

	@Column(name = "pending_message", columnDefinition = "text")
	private String pendingMessage;

	@Column(name = "pending_name", length = 150)
	private String pendingName;

	@Column(name = "pending_email", length = 150)
	private String pendingEmail;

	@Column(name = "pending_document", length = 20)
	private String pendingDocument;

	@Column(name = "pending_assigned_user_id")
	private UUID pendingAssignedUserId;

	@Column(name = "pending_subject", length = 180)
	private String pendingSubject;

	@Column(name = "pending_resume_message", columnDefinition = "text")
	private String pendingResumeMessage;

	@Column(name = "pending_resume_attachments", columnDefinition = "text")
	private String pendingResumeAttachments;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "active_ticket_id")
	private Ticket activeTicket;

	@Column(name = "last_inbound_message_at")
	private OffsetDateTime lastInboundMessageAt;

	@Column(name = "last_outbound_message_at")
	private OffsetDateTime lastOutboundMessageAt;

	@Column(name = "last_ticket_selection_prompt_at")
	private OffsetDateTime lastTicketSelectionPromptAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public User getCompanyOwner() {
		return companyOwner;
	}

	public void setCompanyOwner(User companyOwner) {
		this.companyOwner = companyOwner;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getWhatsappTransportId() {
		return whatsappTransportId;
	}

	public void setWhatsappTransportId(String whatsappTransportId) {
		this.whatsappTransportId = whatsappTransportId;
	}

	public WhatsappConversationStep getCurrentStep() {
		return currentStep;
	}

	public void setCurrentStep(WhatsappConversationStep currentStep) {
		this.currentStep = currentStep;
	}

	public boolean isNormalConversationActive() {
		return normalConversationActive;
	}

	public void setNormalConversationActive(boolean normalConversationActive) {
		this.normalConversationActive = normalConversationActive;
	}

	public Sector getSector() {
		return sector;
	}

	public void setSector(Sector sector) {
		this.sector = sector;
	}

	public String getPendingMessage() {
		return pendingMessage;
	}

	public void setPendingMessage(String pendingMessage) {
		this.pendingMessage = pendingMessage;
	}

	public String getPendingName() {
		return pendingName;
	}

	public void setPendingName(String pendingName) {
		this.pendingName = pendingName;
	}

	public String getPendingEmail() {
		return pendingEmail;
	}

	public void setPendingEmail(String pendingEmail) {
		this.pendingEmail = pendingEmail;
	}

	public String getPendingDocument() {
		return pendingDocument;
	}

	public void setPendingDocument(String pendingDocument) {
		this.pendingDocument = pendingDocument;
	}

	public UUID getPendingAssignedUserId() {
		return pendingAssignedUserId;
	}

	public void setPendingAssignedUserId(UUID pendingAssignedUserId) {
		this.pendingAssignedUserId = pendingAssignedUserId;
	}

	public String getPendingSubject() {
		return pendingSubject;
	}

	public void setPendingSubject(String pendingSubject) {
		this.pendingSubject = pendingSubject;
	}

	public String getPendingResumeMessage() {
		return pendingResumeMessage;
	}

	public void setPendingResumeMessage(String pendingResumeMessage) {
		this.pendingResumeMessage = pendingResumeMessage;
	}

	public String getPendingResumeAttachments() {
		return pendingResumeAttachments;
	}

	public void setPendingResumeAttachments(String pendingResumeAttachments) {
		this.pendingResumeAttachments = pendingResumeAttachments;
	}

	public Ticket getActiveTicket() {
		return activeTicket;
	}

	public void setActiveTicket(Ticket activeTicket) {
		this.activeTicket = activeTicket;
	}

	public OffsetDateTime getLastInboundMessageAt() {
		return lastInboundMessageAt;
	}

	public void setLastInboundMessageAt(OffsetDateTime lastInboundMessageAt) {
		this.lastInboundMessageAt = lastInboundMessageAt;
	}

	public OffsetDateTime getLastOutboundMessageAt() {
		return lastOutboundMessageAt;
	}

	public void setLastOutboundMessageAt(OffsetDateTime lastOutboundMessageAt) {
		this.lastOutboundMessageAt = lastOutboundMessageAt;
	}

	public OffsetDateTime getLastTicketSelectionPromptAt() {
		return lastTicketSelectionPromptAt;
	}

	public void setLastTicketSelectionPromptAt(OffsetDateTime lastTicketSelectionPromptAt) {
		this.lastTicketSelectionPromptAt = lastTicketSelectionPromptAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
