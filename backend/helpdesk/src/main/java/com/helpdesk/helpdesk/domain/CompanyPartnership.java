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
import jakarta.persistence.Table;

@Entity
@Table(name = "company_partnerships")
public class CompanyPartnership {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_company_id", nullable = false)
	private User requesterCompany;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_company_id", nullable = false)
	private User targetCompany;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by_user_id", nullable = false)
	private User requestedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responded_by_user_id")
	private User respondedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CompanyPartnershipStatus status = CompanyPartnershipStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "responded_at")
	private OffsetDateTime respondedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public User getRequesterCompany() {
		return requesterCompany;
	}

	public void setRequesterCompany(User requesterCompany) {
		this.requesterCompany = requesterCompany;
	}

	public User getTargetCompany() {
		return targetCompany;
	}

	public void setTargetCompany(User targetCompany) {
		this.targetCompany = targetCompany;
	}

	public User getRequestedBy() {
		return requestedBy;
	}

	public void setRequestedBy(User requestedBy) {
		this.requestedBy = requestedBy;
	}

	public User getRespondedBy() {
		return respondedBy;
	}

	public void setRespondedBy(User respondedBy) {
		this.respondedBy = respondedBy;
	}

	public CompanyPartnershipStatus getStatus() {
		return status;
	}

	public void setStatus(CompanyPartnershipStatus status) {
		this.status = status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getRespondedAt() {
		return respondedAt;
	}

	public void setRespondedAt(OffsetDateTime respondedAt) {
		this.respondedAt = respondedAt;
	}
}
