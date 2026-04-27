package com.helpdesk.helpdesk.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, unique = true, length = 150, columnDefinition = "citext")
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "phone_number", length = 30)
	private String phoneNumber;

	@Column(name = "whatsapp_transport_id", length = 80)
	private String whatsappTransportId;

	@Column(name = "document_number", length = 20)
	private String documentNumber;

	@Column(name = "company_name", length = 150)
	private String companyName;

	@Column(name = "company_document", length = 20)
	private String companyDocument;

	@Enumerated(EnumType.STRING)
	@Column(name = "company_type", length = 20)
	private CompanyType companyType;

	@ManyToOne
	@JoinColumn(name = "company_owner_id")
	private User companyOwner;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "is_email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "is_simplified", nullable = false)
	private boolean simplified;

	@Column(name = "last_login_at")
	private OffsetDateTime lastLoginAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@ManyToMany
	@JoinTable(
		name = "user_roles",
		joinColumns = @JoinColumn(name = "user_id"),
		inverseJoinColumns = @JoinColumn(name = "role_id")
	)
	private Set<Role> roles = new LinkedHashSet<>();

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

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
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

	public String getDocumentNumber() {
		return documentNumber;
	}

	public void setDocumentNumber(String documentNumber) {
		this.documentNumber = documentNumber;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getCompanyDocument() {
		return companyDocument;
	}

	public void setCompanyDocument(String companyDocument) {
		this.companyDocument = companyDocument;
	}

	public CompanyType getCompanyType() {
		return companyType;
	}

	public void setCompanyType(CompanyType companyType) {
		this.companyType = companyType;
	}

	public User getCompanyOwner() {
		return companyOwner;
	}

	public void setCompanyOwner(User companyOwner) {
		this.companyOwner = companyOwner;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public boolean isSimplified() {
		return simplified;
	}

	public void setSimplified(boolean simplified) {
		this.simplified = simplified;
	}

	public OffsetDateTime getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(OffsetDateTime lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public OffsetDateTime getDeletedAt() {
		return deletedAt;
	}

	public Set<Role> getRoles() {
		return roles;
	}
}
