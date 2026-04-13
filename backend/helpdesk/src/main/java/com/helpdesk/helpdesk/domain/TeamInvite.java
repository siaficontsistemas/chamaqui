package com.helpdesk.helpdesk.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_invites")
public class TeamInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 150, columnDefinition = "citext")
	private String email;

	@Column(name = "invited_name", nullable = false, length = 150)
	private String invitedName;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invited_by", nullable = false)
	private User invitedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accepted_user_id")
	private User acceptedUser;

	@Column(name = "token_hash", nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InviteStatus status = InviteStatus.PENDING;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "accepted_at")
	private OffsetDateTime acceptedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@ManyToMany
	@JoinTable(
		name = "team_invite_sectors",
		joinColumns = @JoinColumn(name = "invite_id"),
		inverseJoinColumns = @JoinColumn(name = "sector_id")
	)
	private Set<Sector> sectors = new LinkedHashSet<>();

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

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getInvitedName() {
		return invitedName;
	}

	public void setInvitedName(String invitedName) {
		this.invitedName = invitedName;
	}

	public User getInvitedBy() {
		return invitedBy;
	}

	public void setInvitedBy(User invitedBy) {
		this.invitedBy = invitedBy;
	}

	public User getAcceptedUser() {
		return acceptedUser;
	}

	public void setAcceptedUser(User acceptedUser) {
		this.acceptedUser = acceptedUser;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public InviteStatus getStatus() {
		return status;
	}

	public void setStatus(InviteStatus status) {
		this.status = status;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public OffsetDateTime getAcceptedAt() {
		return acceptedAt;
	}

	public void setAcceptedAt(OffsetDateTime acceptedAt) {
		this.acceptedAt = acceptedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Set<Sector> getSectors() {
		return sectors;
	}
}
