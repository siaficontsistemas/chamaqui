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
import jakarta.persistence.Table;

@Entity
@Table(name = "company_access_requests")
public class CompanyAccessRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_user_id")
	private User requesterUser;

	@Column(name = "requester_name", length = 150)
	private String requesterName;

	@Column(name = "requester_email", length = 150)
	private String requesterEmail;

	@Column(name = "requester_document_number", length = 20)
	private String requesterDocumentNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_company_id", nullable = false)
	private User targetCompany;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responded_by_user_id")
	private User respondedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_type", nullable = false, length = 20)
	private CompanyAccessRequestType requestType = CompanyAccessRequestType.USER_REQUEST;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CompanyAccessRequestStatus status = CompanyAccessRequestStatus.PENDING;

	@Column(name = "invite_token_hash", length = 120)
	private String inviteTokenHash;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Column(name = "responded_at")
	private OffsetDateTime respondedAt;

	@SuppressWarnings("unused")
	@PrePersist
	void onCreate() {
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public User getRequesterUser() {
		return requesterUser;
	}

	public void setRequesterUser(User requesterUser) {
		this.requesterUser = requesterUser;
	}

	public String getRequesterName() {
		return requesterName;
	}

	public void setRequesterName(String requesterName) {
		this.requesterName = requesterName;
	}

	public String getRequesterEmail() {
		return requesterEmail;
	}

	public void setRequesterEmail(String requesterEmail) {
		this.requesterEmail = requesterEmail;
	}

	public String getRequesterDocumentNumber() {
		return requesterDocumentNumber;
	}

	public void setRequesterDocumentNumber(String requesterDocumentNumber) {
		this.requesterDocumentNumber = requesterDocumentNumber;
	}

	public User getTargetCompany() {
		return targetCompany;
	}

	public void setTargetCompany(User targetCompany) {
		this.targetCompany = targetCompany;
	}

	public User getRespondedBy() {
		return respondedBy;
	}

	public void setRespondedBy(User respondedBy) {
		this.respondedBy = respondedBy;
	}

	public CompanyAccessRequestType getRequestType() {
		return requestType;
	}

	public void setRequestType(CompanyAccessRequestType requestType) {
		this.requestType = requestType;
	}

	public CompanyAccessRequestStatus getStatus() {
		return status;
	}

	public void setStatus(CompanyAccessRequestStatus status) {
		this.status = status;
	}

	public String getInviteTokenHash() {
		return inviteTokenHash;
	}

	public void setInviteTokenHash(String inviteTokenHash) {
		this.inviteTokenHash = inviteTokenHash;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public OffsetDateTime getRespondedAt() {
		return respondedAt;
	}

	public void setRespondedAt(OffsetDateTime respondedAt) {
		this.respondedAt = respondedAt;
	}
}
