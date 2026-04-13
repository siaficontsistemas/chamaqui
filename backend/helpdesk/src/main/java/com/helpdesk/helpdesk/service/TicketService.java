package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketPriority;
import com.helpdesk.helpdesk.domain.TicketStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketPriorityRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketStatusRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final SectorRepository sectorRepository;
	private final TicketStatusRepository ticketStatusRepository;
	private final TicketPriorityRepository ticketPriorityRepository;

	public TicketService(
		TicketRepository ticketRepository,
		UserRepository userRepository,
		SectorRepository sectorRepository,
		TicketStatusRepository ticketStatusRepository,
		TicketPriorityRepository ticketPriorityRepository
	) {
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.sectorRepository = sectorRepository;
		this.ticketStatusRepository = ticketStatusRepository;
		this.ticketPriorityRepository = ticketPriorityRepository;
	}

	@Transactional(readOnly = true)
	public List<TicketResponse> list(String status) {
		List<Ticket> tickets = (status == null || status.isBlank())
			? ticketRepository.findAllByOrderByCreatedAtDesc()
			: ticketRepository.findByStatusCodeOrderByCreatedAtDesc(status.toUpperCase(Locale.ROOT));

		return tickets.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public TicketSummaryResponse summary() {
		List<Ticket> tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
		long open = tickets.stream().filter(ticket -> "OPEN".equals(ticket.getStatus().getCode())).count();
		long inProgress = tickets.stream().filter(ticket -> "IN_PROGRESS".equals(ticket.getStatus().getCode())).count();
		long closed = tickets.stream().filter(ticket -> "CLOSED".equals(ticket.getStatus().getCode())).count();
		return new TicketSummaryResponse(tickets.size(), open, inProgress, closed);
	}

	@Transactional
	public TicketResponse create(CreateTicketRequest request) {
		User requester = userRepository.findByEmailIgnoreCase(request.requesterEmail().trim())
			.orElseThrow(() -> new NotFoundException("Solicitante não encontrado."));
		TicketStatus status = ticketStatusRepository.findByCode("OPEN")
			.orElseThrow(() -> new NotFoundException("Status padrão de abertura não encontrado."));
		TicketPriority priority = ticketPriorityRepository.findByCode(request.priorityCode().trim().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Prioridade não encontrada."));

		Ticket ticket = new Ticket();
		ticket.setProtocol(nextProtocol());
		ticket.setTitle(request.title().trim());
		ticket.setDescription(request.description().trim());
		ticket.setRequester(requester);
		ticket.setSector(sectorRepository.findById(request.sectorId())
			.orElseThrow(() -> new NotFoundException("Setor não encontrado.")));
		ticket.setStatus(status);
		ticket.setPriority(priority);

		return toResponse(ticketRepository.save(ticket));
	}

	private String nextProtocol() {
		long nextNumber = ticketRepository.count() + 1;
		return "HD-2026-" + String.format("%04d", nextNumber);
	}

	private TicketResponse toResponse(Ticket ticket) {
		return new TicketResponse(
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			ticket.getDescription(),
			ticket.getRequester().getFullName(),
			ticket.getRequester().getEmail(),
			ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
			ticket.getSector().getName(),
			ticket.getStatus().getCode(),
			ticket.getStatus().getName(),
			ticket.getPriority().getCode(),
			ticket.getPriority().getName(),
			ticket.getOpenedAt(),
			ticket.getClosedAt()
		);
	}
}
