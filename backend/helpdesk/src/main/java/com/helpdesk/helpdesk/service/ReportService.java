package com.helpdesk.helpdesk.service;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.report.PersonalReportRowResponse;
import com.helpdesk.helpdesk.repository.TicketMessageRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;

@Service
public class ReportService {

	private final TicketRepository ticketRepository;
	private final TicketMessageRepository ticketMessageRepository;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;

	public ReportService(
		TicketRepository ticketRepository,
		TicketMessageRepository ticketMessageRepository,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.ticketRepository = ticketRepository;
		this.ticketMessageRepository = ticketMessageRepository;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	@Transactional(readOnly = true)
	public List<PersonalReportRowResponse> getPersonalReport(String email) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(email)
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado para o relatório."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		Map<YearMonth, Long> createdTicketsByMonth = ticketRepository.findVisibleByEmailOrderByCreatedAtDesc(email).stream()
			.filter(ticket -> ticket.getRequester().getId().equals(user.getId()))
			.collect(Collectors.groupingBy(
				ticket -> YearMonth.from(ticket.getOpenedAt()),
				Collectors.counting()
			));

		Map<YearMonth, Long> repliedTicketsByMonth = ticketMessageRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
			.filter(this::isReplyMessage)
			.collect(Collectors.groupingBy(
				message -> YearMonth.from(message.getCreatedAt()),
				Collectors.counting()
			));

		Map<YearMonth, PersonalReportCounters> countersByMonth = new LinkedHashMap<>();
		createdTicketsByMonth.forEach((yearMonth, count) ->
			countersByMonth.computeIfAbsent(yearMonth, ignored -> new PersonalReportCounters()).createdTickets = count
		);
		repliedTicketsByMonth.forEach((yearMonth, count) ->
			countersByMonth.computeIfAbsent(yearMonth, ignored -> new PersonalReportCounters()).repliedTickets = count
		);

		return countersByMonth.entrySet().stream()
			.sorted(Map.Entry.<YearMonth, PersonalReportCounters>comparingByKey(Comparator.reverseOrder()))
			.map(entry -> {
				PersonalReportCounters counters = entry.getValue();
				return new PersonalReportRowResponse(
					String.valueOf(entry.getKey().getYear()),
					entry.getKey().getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR")),
					counters.createdTickets,
					counters.repliedTickets
				);
			})
			.toList();
	}

	private boolean isReplyMessage(TicketMessage message) {
		Ticket ticket = message.getTicket();
		return ticket != null
			&& ticket.getOpenedAt() != null
			&& !message.getCreatedAt().isEqual(ticket.getOpenedAt());
	}

	private static final class PersonalReportCounters {
		private long createdTickets;
		private long repliedTickets;
	}
}
