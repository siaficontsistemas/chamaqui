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
@Table(name = "company_partnership_notifications")
public class CompanyPartnershipNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "company_partnership_id")
	private UUID companyPartnershipId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private User recipient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_user_id", nullable = false)
	private User actorUser;

	@Column(name = "requester_company_id", nullable = false)
	private UUID requesterCompanyId;

	@Column(name = "requester_company_name", nullable = false, length = 150)
	private String requesterCompanyName;

	@Column(name = "target_company_id", nullable = false)
	private UUID targetCompanyId;

	@Column(name = "target_company_name", nullable = false, length = 150)
	private String targetCompanyName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CompanyPartnershipNotificationType type;

	@Column(nullable = false)
	private boolean hidden;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@SuppressWarnings("unused")
	@PrePersist
	void onCreate() {
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getCompanyPartnershipId() {
		return companyPartnershipId;
	}

	public void setCompanyPartnershipId(UUID companyPartnershipId) {
		this.companyPartnershipId = companyPartnershipId;
	}

	public User getRecipient() {
		return recipient;
	}

	public void setRecipient(User recipient) {
		this.recipient = recipient;
	}

	public User getActorUser() {
		return actorUser;
	}

	public void setActorUser(User actorUser) {
		this.actorUser = actorUser;
	}

	public UUID getRequesterCompanyId() {
		return requesterCompanyId;
	}

	public void setRequesterCompanyId(UUID requesterCompanyId) {
		this.requesterCompanyId = requesterCompanyId;
	}

	public String getRequesterCompanyName() {
		return requesterCompanyName;
	}

	public void setRequesterCompanyName(String requesterCompanyName) {
		this.requesterCompanyName = requesterCompanyName;
	}

	public UUID getTargetCompanyId() {
		return targetCompanyId;
	}

	public void setTargetCompanyId(UUID targetCompanyId) {
		this.targetCompanyId = targetCompanyId;
	}

	public String getTargetCompanyName() {
		return targetCompanyName;
	}

	public void setTargetCompanyName(String targetCompanyName) {
		this.targetCompanyName = targetCompanyName;
	}

	public CompanyPartnershipNotificationType getType() {
		return type;
	}

	public void setType(CompanyPartnershipNotificationType type) {
		this.type = type;
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
}
