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
@Table(name = "calendar_obligations")
public class CalendarObligation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_owner_id", nullable = false)
	private User companyOwner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "linked_company_owner_id", nullable = false)
	private User linkedCompanyOwner;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
		name = "calendar_obligation_recipients",
		joinColumns = @JoinColumn(name = "obligation_id"),
		inverseJoinColumns = @JoinColumn(name = "recipient_id")
	)
	private Set<User> recipients = new LinkedHashSet<>();

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
		name = "calendar_obligation_tickets",
		joinColumns = @JoinColumn(name = "obligation_id"),
		inverseJoinColumns = @JoinColumn(name = "ticket_id")
	)
	private Set<Ticket> linkedTickets = new LinkedHashSet<>();

	@Column(nullable = false, length = 180)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority_code", nullable = false, length = 20)
	private CalendarObligationPriority priority = CalendarObligationPriority.MEDIUM;

	@Column(name = "due_at", nullable = false)
	private OffsetDateTime dueAt;

	@Column(name = "reminder_at")
	private OffsetDateTime reminderAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@SuppressWarnings("unused")
	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@SuppressWarnings("unused")
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

	public User getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}

	public Set<User> getRecipients() {
		return recipients;
	}

	public void setRecipients(Set<User> recipients) {
		this.recipients = recipients == null ? new LinkedHashSet<>() : new LinkedHashSet<>(recipients);
	}

	public Set<Ticket> getLinkedTickets() {
		return linkedTickets;
	}

	public void setLinkedTickets(Set<Ticket> linkedTickets) {
		this.linkedTickets = linkedTickets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(linkedTickets);
	}

	public User getLinkedCompanyOwner() {
		return linkedCompanyOwner;
	}

	public void setLinkedCompanyOwner(User linkedCompanyOwner) {
		this.linkedCompanyOwner = linkedCompanyOwner;
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

	public CalendarObligationPriority getPriority() {
		return priority;
	}

	public void setPriority(CalendarObligationPriority priority) {
		this.priority = priority == null ? CalendarObligationPriority.MEDIUM : priority;
	}

	public OffsetDateTime getDueAt() {
		return dueAt;
	}

	public void setDueAt(OffsetDateTime dueAt) {
		this.dueAt = dueAt;
	}

	public OffsetDateTime getReminderAt() {
		return reminderAt;
	}

	public void setReminderAt(OffsetDateTime reminderAt) {
		this.reminderAt = reminderAt;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(OffsetDateTime completedAt) {
		this.completedAt = completedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
