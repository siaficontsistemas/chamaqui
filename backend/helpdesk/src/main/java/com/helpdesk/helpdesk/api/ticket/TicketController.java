package com.helpdesk.helpdesk.api.ticket;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketResponse> list(@RequestParam(required = false) String status) {
		return ticketService.list(status);
	}

	@GetMapping("/summary")
	public TicketSummaryResponse summary() {
		return ticketService.summary();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
		return ticketService.create(request);
	}
}
