package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.reference.ReferenceItemResponse;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.TicketPriorityRepository;
import com.helpdesk.helpdesk.repository.TicketStatusRepository;

@Service
public class ReferenceService {

	private final RoleRepository roleRepository;
	private final TicketStatusRepository ticketStatusRepository;
	private final TicketPriorityRepository ticketPriorityRepository;

	public ReferenceService(
		RoleRepository roleRepository,
		TicketStatusRepository ticketStatusRepository,
		TicketPriorityRepository ticketPriorityRepository
	) {
		this.roleRepository = roleRepository;
		this.ticketStatusRepository = ticketStatusRepository;
		this.ticketPriorityRepository = ticketPriorityRepository;
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getRoles() {
		return roleRepository.findAll().stream()
			.sorted(java.util.Comparator.comparing(role -> role.getName().toLowerCase()))
			.map(role -> new ReferenceItemResponse(role.getId(), role.getCode(), role.getName(), null))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getTicketStatuses() {
		return ticketStatusRepository.findAllByOrderBySortOrderAsc().stream()
			.map(status -> new ReferenceItemResponse(status.getId(), status.getCode(), status.getName(), status.getSortOrder()))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getTicketPriorities() {
		return ticketPriorityRepository.findAllByOrderBySortOrderAsc().stream()
			.map(priority -> new ReferenceItemResponse(priority.getId(), priority.getCode(), priority.getName(), priority.getSortOrder()))
			.toList();
	}
}
