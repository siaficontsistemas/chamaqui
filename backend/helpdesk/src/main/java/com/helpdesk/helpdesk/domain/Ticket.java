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
@Table(name = "tickets")
public class Ticket {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 30)
	private String protocol;

	@Column(nullable = false, length = 180)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false)
	private User requester;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_to")
	private User assignedTo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pending_transfer_to")
	private User pendingTransferTo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pending_transfer_requested_by")
	private User pendingTransferRequestedBy;

	@Column(name = "pending_transfer_requested_at")
	private OffsetDateTime pendingTransferRequestedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sector_id", nullable = false)
	private Sector sector;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "status_id", nullable = false)
	private TicketStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "priority_id", nullable = false)
	private TicketPriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TicketChannel channel = TicketChannel.PORTAL;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", length = 30)
	private TicketCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "system_error_type", length = 20)
	private TicketSystemErrorType systemErrorType;

	@Column(name = "opened_at", nullable = false)
	private OffsetDateTime openedAt;

	@Column(name = "first_response_at")
	private OffsetDateTime firstResponseAt;

	@Column(name = "resolved_at")
	private OffsetDateTime resolvedAt;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "copy_email", length = 255)
	private String copyEmail;

	@Column(name = "due_at")
	private OffsetDateTime dueAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		if (openedAt == null) {
			openedAt = now;
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

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public User getRequester() {
		return requester;
	}

	public void setRequester(User requester) {
		this.requester = requester;
	}

	public User getAssignedTo() {
		return assignedTo;
	}

	public void setAssignedTo(User assignedTo) {
		this.assignedTo = assignedTo;
	}

	public User getPendingTransferTo() {
		return pendingTransferTo;
	}

	public void setPendingTransferTo(User pendingTransferTo) {
		this.pendingTransferTo = pendingTransferTo;
	}

	public User getPendingTransferRequestedBy() {
		return pendingTransferRequestedBy;
	}

	public void setPendingTransferRequestedBy(User pendingTransferRequestedBy) {
		this.pendingTransferRequestedBy = pendingTransferRequestedBy;
	}

	public OffsetDateTime getPendingTransferRequestedAt() {
		return pendingTransferRequestedAt;
	}

	public void setPendingTransferRequestedAt(OffsetDateTime pendingTransferRequestedAt) {
		this.pendingTransferRequestedAt = pendingTransferRequestedAt;
	}

	public Sector getSector() {
		return sector;
	}

	public void setSector(Sector sector) {
		this.sector = sector;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public void setStatus(TicketStatus status) {
		this.status = status;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public void setPriority(TicketPriority priority) {
		this.priority = priority;
	}

	public TicketChannel getChannel() {
		return channel;
	}

	public void setChannel(TicketChannel channel) {
		this.channel = channel;
	}

	public TicketCategory getCategory() { return category; }
	public void setCategory(TicketCategory category) { this.category = category; }
	public TicketSystemErrorType getSystemErrorType() { return systemErrorType; }
	public void setSystemErrorType(TicketSystemErrorType systemErrorType) { this.systemErrorType = systemErrorType; }

	public OffsetDateTime getOpenedAt() {
		return openedAt;
	}

	public void setOpenedAt(OffsetDateTime openedAt) {
		this.openedAt = openedAt;
	}

	public OffsetDateTime getFirstResponseAt() {
		return firstResponseAt;
	}

	public void setFirstResponseAt(OffsetDateTime firstResponseAt) {
		this.firstResponseAt = firstResponseAt;
	}

	public OffsetDateTime getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(OffsetDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public OffsetDateTime getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(OffsetDateTime closedAt) {
		this.closedAt = closedAt;
	}

	public String getCopyEmail() {
		return copyEmail;
	}

	public void setCopyEmail(String copyEmail) {
		this.copyEmail = copyEmail;
	}

	public OffsetDateTime getDueAt() {
		return dueAt;
	}

	public void setDueAt(OffsetDateTime dueAt) {
		this.dueAt = dueAt;
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

	public void setDeletedAt(OffsetDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}
}
