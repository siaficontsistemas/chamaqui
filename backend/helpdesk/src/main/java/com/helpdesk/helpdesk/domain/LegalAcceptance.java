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
@Table(name = "legal_acceptances")
public class LegalAcceptance {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 30)
	private LegalDocumentType documentType;

	@Column(nullable = false, length = 40)
	private String version;

	@Column(name = "accepted_at", nullable = false)
	private OffsetDateTime acceptedAt;

	@Column(name = "evidence_ip", length = 80)
	private String evidenceIp;

	@Column(name = "evidence_user_agent", length = 255)
	private String evidenceUserAgent;

	@Column(nullable = false, length = 40)
	private String source;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = OffsetDateTime.now();
		}
		if (acceptedAt == null) {
			acceptedAt = createdAt;
		}
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public LegalDocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(LegalDocumentType documentType) {
		this.documentType = documentType;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public OffsetDateTime getAcceptedAt() {
		return acceptedAt;
	}

	public void setAcceptedAt(OffsetDateTime acceptedAt) {
		this.acceptedAt = acceptedAt;
	}

	public String getEvidenceIp() {
		return evidenceIp;
	}

	public void setEvidenceIp(String evidenceIp) {
		this.evidenceIp = evidenceIp;
	}

	public String getEvidenceUserAgent() {
		return evidenceUserAgent;
	}

	public void setEvidenceUserAgent(String evidenceUserAgent) {
		this.evidenceUserAgent = evidenceUserAgent;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}
}
