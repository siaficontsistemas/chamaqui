# Debug Session: whatsapp-ticket-switch [OPEN]

## Sintoma
- No WhatsApp, após o sistema listar chamados abertos, o usuário envia `conversa normal`.
- Em seguida, ao enviar `trocar chamado`, o sistema responde que não possui chamado aberto para continuar, mesmo havendo chamados abertos.
- O print mostra que os chamados foram reconhecidos instantes antes e depois "somem" na mesma conversa.

## Impacto
- O roteamento entre conversa normal e chamado aberto fica inconsistente.
- Usuários e atendentes perdem a capacidade de retomar chamados abertos já existentes.

## Hipóteses Falsificáveis
1. A conversa usada no segundo comando `trocar chamado` não é a mesma instância persistida após `conversa normal`, então o contexto do solicitante se perde entre mensagens.
2. O `activeTicket` permanece salvo, mas chega lazy/parcial ou sem `requester`, fazendo `loadOpenTicketsForConversation()` montar filtros vazios.
3. O lookup por telefone/transportId muda entre as duas mensagens e passa a apontar para um identificador diferente, retornando lista vazia.
4. A query `findOpenWhatsappTicketsForRouting()` recebe parâmetros corretos, mas algum dos filtros (`requesterId`, email, phoneNumber, whatsappTransportId) chega em branco no segundo passo.
5. Há mais de uma conversa concorrente para o mesmo contato e o backend está resolvendo a conversa errada na segunda mensagem.

## Plano Inicial
1. Instrumentar a resolução da conversa e os parâmetros usados em `conversa normal` e `trocar chamado`.
2. Instrumentar `loadOpenTicketsForConversation()` e a query de chamados abertos.
3. Reproduzir o fluxo exato do print em produção/staging.
4. Só então aplicar a correção mínima baseada nos logs.

## Evidências
- Pendente.

## Próximo Passo
- Subir servidor de debug e adicionar instrumentação mínima no `WhatsappWebhookService` e `TicketRepository`.
