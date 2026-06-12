package com.helpdesk.helpdesk.api.calendar;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.helpdesk.helpdesk.dto.calendar.CalendarLinkedTicketResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarObligationResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarTicketSearchResponse;
import com.helpdesk.helpdesk.service.CalendarService;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

	private MockMvc mockMvc;

	@Mock
	private CalendarService calendarService;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new CalendarController(calendarService))
			.setMessageConverters(new MappingJackson2HttpMessageConverter())
			.build();
	}

	@Test
	void shouldReturnLinkedTicketsInsideCalendarObligationResponse() throws Exception {
		UUID obligationId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		UUID ticketId = UUID.randomUUID();

		when(calendarService.listVisible("admin@empresa.com")).thenReturn(List.of(
			new CalendarObligationResponse(
				obligationId,
				"Entrega fiscal",
				"Descrição",
				"MEDIUM",
				OffsetDateTime.parse("2026-06-10T10:00:00Z"),
				OffsetDateTime.parse("2026-06-09T08:00:00Z"),
				null,
				OffsetDateTime.parse("2026-06-01T10:00:00Z"),
				OffsetDateTime.parse("2026-06-02T10:00:00Z"),
				"Administrador",
				List.of("Silvia Freire"),
				List.of("11122233344"),
				"Lopes Consultoria",
				companyId,
				"Lopes Consultoria",
				List.of(new CalendarLinkedTicketResponse(
					ticketId,
					"CA-2026-0042",
					"Chamado vinculado",
					"CLOSED",
					"Fechado",
					"José Marcos"
				)),
				"UPCOMING",
				false
			)
		));

		mockMvc.perform(get("/api/v1/calendar/obligations").param("email", "admin@empresa.com"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].linkedTickets[0].protocol").value("CA-2026-0042"))
			.andExpect(jsonPath("$[0].linkedTickets[0].statusCode").value("CLOSED"))
			.andExpect(jsonPath("$[0].linkedTickets[0].title").value("Chamado vinculado"));
	}

	@Test
	void shouldReturnPaginatedTicketSearchResults() throws Exception {
		UUID ticketId = UUID.randomUUID();

		when(calendarService.searchTickets("admin@empresa.com", "siga", 0, 20)).thenReturn(
			new CalendarTicketSearchResponse(
				List.of(new CalendarLinkedTicketResponse(
					ticketId,
					"CA-2026-0007",
					"Prazo SIGA",
					"OPEN",
					"Aberto",
					"José Marcos"
				)),
				true
			)
		);

		mockMvc.perform(
			get("/api/v1/calendar/tickets/search")
				.param("email", "admin@empresa.com")
				.param("query", "siga")
				.param("offset", "0")
				.param("limit", "20")
		)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tickets[0].protocol").value("CA-2026-0007"))
			.andExpect(jsonPath("$.tickets[0].statusName").value("Aberto"))
			.andExpect(jsonPath("$.hasMore").value(true));
	}

	@Test
	void shouldUpdateLinkedTicketsThroughMainObligationEditEndpoint() throws Exception {
		UUID obligationId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		UUID ticketId = UUID.randomUUID();

		when(calendarService.update(
			org.mockito.ArgumentMatchers.eq(obligationId),
			org.mockito.ArgumentMatchers.any()
		)).thenReturn(new CalendarObligationResponse(
			obligationId,
			"Entrega fiscal",
			"Descrição",
			"MEDIUM",
			OffsetDateTime.parse("2026-06-10T10:00:00Z"),
			null,
			null,
			OffsetDateTime.parse("2026-06-01T10:00:00Z"),
			OffsetDateTime.parse("2026-06-02T10:00:00Z"),
			"Administrador",
			List.of("Silvia Freire"),
			List.of("11122233344"),
			"Lopes Consultoria",
			companyId,
			"Lopes Consultoria",
			List.of(new CalendarLinkedTicketResponse(
				ticketId,
				"CA-2026-0042",
				"Chamado vinculado",
				"CLOSED",
				"Fechado",
				"José Marcos"
			)),
			"UPCOMING",
			false
		));

		mockMvc.perform(
			put("/api/v1/calendar/obligations/{obligationId}", obligationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "Entrega fiscal",
					  "description": "Descrição",
					  "dueAt": "2026-06-10T10:00:00Z",
					  "reminderAt": null,
					  "priority": "MEDIUM",
					  "linkedCompanyOwnerId": "%s",
					  "linkedTicketIds": ["%s"],
					  "recipientDocumentNumbers": ["11122233344"],
					  "updatedByEmail": "admin@empresa.com"
					}
					""".formatted(companyId, ticketId))
		)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedTickets[0].protocol").value("CA-2026-0042"));
	}
}
