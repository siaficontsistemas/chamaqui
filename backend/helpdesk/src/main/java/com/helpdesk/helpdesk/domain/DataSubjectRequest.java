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

@Entity
@Table(name = "data_subject_requests")
public class DataSubjectRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_user_id", nullable = false)
	private User requesterUser;

	@Column(name = "tenant_owner_user_id")
	private UUID tenantOwnerUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_type", nullable = false, length = 30)
	private DataSubjectRightType requestType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DataSubjectRequestStatus status = DataSubjectRequestStatus.OPEN;

	@Column(name = "requester_full_name", nullable = false, length = 150)
	private String requesterFullName;

	@Column(name = "requester_email", nullable = false, length = 150)
	private String requesterEmail;

	@Column(name = "request_description", nullable = false, length = 4000)
	private String requestDescription;

	@Column(name = "response_summary", length = 4000)
	private String responseSummary;

	@Column(name = "internal_notes", length = 4000)
	private String internalNotes;

	@Column(name = "requested_at", nullable = false)
	private OffsetDateTime requestedAt;

	@Column(name = "due_at", nullable = false)
	private OffsetDateTime dueAt;

	@Column(name = "resolved_at")
	private OffsetDateTime resolvedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		if (requestedAt == null) {
			requestedAt = now;
		}
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

	public User getRequesterUser() {
		return requesterUser;
	}

	public void setRequesterUser(User requesterUser) {
		this.requesterUser = requesterUser;
	}

	public UUID getTenantOwnerUserId() {
		return tenantOwnerUserId;
	}

	public void setTenantOwnerUserId(UUID tenantOwnerUserId) {
		this.tenantOwnerUserId = tenantOwnerUserId;
	}

	public DataSubjectRightType getRequestType() {
		return requestType;
	}

	public void setRequestType(DataSubjectRightType requestType) {
		this.requestType = requestType;
	}

	public DataSubjectRequestStatus getStatus() {
		return status;
	}

	public void setStatus(DataSubjectRequestStatus status) {
		this.status = status;
	}

	public String getRequesterFullName() {
		return requesterFullName;
	}

	public void setRequesterFullName(String requesterFullName) {
		this.requesterFullName = requesterFullName;
	}

	public String getRequesterEmail() {
		return requesterEmail;
	}

	public void setRequesterEmail(String requesterEmail) {
		this.requesterEmail = requesterEmail;
	}

	public String getRequestDescription() {
		return requestDescription;
	}

	public void setRequestDescription(String requestDescription) {
		this.requestDescription = requestDescription;
	}

	public String getResponseSummary() {
		return responseSummary;
	}

	public void setResponseSummary(String responseSummary) {
		this.responseSummary = responseSummary;
	}

	public String getInternalNotes() {
		return internalNotes;
	}

	public void setInternalNotes(String internalNotes) {
		this.internalNotes = internalNotes;
	}

	public OffsetDateTime getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(OffsetDateTime requestedAt) {
		this.requestedAt = requestedAt;
	}

	public OffsetDateTime getDueAt() {
		return dueAt;
	}

	public void setDueAt(OffsetDateTime dueAt) {
		this.dueAt = dueAt;
	}

	public OffsetDateTime getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(OffsetDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}
}
