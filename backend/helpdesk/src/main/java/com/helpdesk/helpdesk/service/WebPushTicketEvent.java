package com.helpdesk.helpdesk.service;

import java.util.UUID;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.User;

public record WebPushTicketEvent(UUID recipientId, String title, String body, String url, String tag) {
    static WebPushTicketEvent assignment(Ticket ticket, User recipient) {
        return new WebPushTicketEvent(recipient.getId(), "Novo chamado " + ticket.getProtocol(),
            (ticket.getRequester() == null ? "Um cliente" : ticket.getRequester().getFullName()) + " abriu: " + ticket.getTitle(),
            "/tickets/" + ticket.getId(), "ticket-assignment-" + ticket.getId());
    }
    static WebPushTicketEvent reply(Ticket ticket, TicketMessage message, User recipient) {
        boolean client = ticket.getRequester() != null && ticket.getRequester().getId().equals(recipient.getId());
        String title = client ? "Chamado " + ticket.getProtocol() + " foi respondido" : "Nova réplica em " + ticket.getProtocol();
        String body = message.getMessage() == null || message.getMessage().isBlank() ? ticket.getTitle() : message.getMessage();
        if (body.length() > 160) body = body.substring(0, 157) + "...";
        return new WebPushTicketEvent(recipient.getId(), title, body, "/tickets/" + ticket.getId(),
            "ticket-reply-" + message.getId());
    }
    static WebPushTicketEvent closure(Ticket ticket, User recipient) {
        return new WebPushTicketEvent(recipient.getId(), "Chamado " + ticket.getProtocol() + " foi fechado",
            ticket.getTitle(), "/tickets/" + ticket.getId(), "ticket-closure-" + ticket.getId());
    }
}
