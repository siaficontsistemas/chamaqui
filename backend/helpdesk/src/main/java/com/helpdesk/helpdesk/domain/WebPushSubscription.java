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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "web_push_subscriptions")
public class WebPushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, columnDefinition = "text")
	private String endpoint;

	@Column(nullable = false, columnDefinition = "text")
	private String p256dh;

	@Column(name = "auth_secret", nullable = false, columnDefinition = "text")
	private String auth;

	@Column(name = "expiration_time")
	private Double expirationTime;

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

	public UUID getId() { return id; }
	public User getUser() { return user; }
	public void setUser(User user) { this.user = user; }
	public String getEndpoint() { return endpoint; }
	public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
	public String getP256dh() { return p256dh; }
	public void setP256dh(String p256dh) { this.p256dh = p256dh; }
	public String getAuth() { return auth; }
	public void setAuth(String auth) { this.auth = auth; }
	public Double getExpirationTime() { return expirationTime; }
	public void setExpirationTime(Double expirationTime) { this.expirationTime = expirationTime; }
}
