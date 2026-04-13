package com.helpdesk.helpdesk.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_statuses")
public class TicketStatus {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 40)
	private String code;

	@Column(nullable = false, unique = true, length = 80)
	private String name;

	@Column(nullable = false)
	private Integer sortOrder;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}
}
