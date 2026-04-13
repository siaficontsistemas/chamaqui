package com.helpdesk.helpdesk.service;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.report.PersonalReportRowResponse;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ReportService {

	private final UserRepository userRepository;
	private final TicketRepository ticketRepository;

	public ReportService(UserRepository userRepository, TicketRepository ticketRepository) {
		this.userRepository = userRepository;
		this.ticketRepository = ticketRepository;
	}

	@Transactional(readOnly = true)
	public List<PersonalReportRowResponse> getPersonalReport(String email) {
		User user = userRepository.findByEmailIgnoreCase(email)
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado para o relatório."));

		Map<YearMonth, Long> ticketsByMonth = ticketRepository.findAllByOrderByCreatedAtDesc().stream()
			.filter(ticket -> belongsToUser(user, ticket))
			.collect(Collectors.groupingBy(
				ticket -> YearMonth.from(ticket.getOpenedAt()),
				Collectors.counting()
			));

		return ticketsByMonth.entrySet().stream()
			.sorted(Map.Entry.<YearMonth, Long>comparingByKey(Comparator.reverseOrder()))
			.map(entry -> new PersonalReportRowResponse(
				String.valueOf(entry.getKey().getYear()),
				entry.getKey().getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")),
				formatWorkTime(entry.getValue())
			))
			.toList();
	}

	private boolean belongsToUser(User user, Ticket ticket) {
		return (ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(user.getId()))
			|| ticket.getRequester().getId().equals(user.getId());
	}

	private String formatWorkTime(long ticketCount) {
		long totalMinutes = ticketCount * 80;
		long hours = totalMinutes / 60;
		long minutes = totalMinutes % 60;
		return hours + ":" + String.format("%02d", minutes);
	}
}
