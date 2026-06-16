# Notificacoes Em Tempo Real De Chamados

## Logica

- O backend continua persistindo as notificacoes de `ticket-assignment` e `ticket-reply` como fonte oficial da campainha e do contador.
- Ao criar um novo chamado, cada destinatario da notificacao recebe um evento WebSocket `CREATED` do tipo `ticket-assignment`.
- Ao receber nova mensagem do cliente depois da resposta da equipe, cada destinatario recebe um evento WebSocket `CREATED` do tipo `ticket-reply`.
- Quando um administrador ou funcionario responde o chamado, o backend oculta as notificacoes ativas daquele ticket e publica um evento WebSocket `CLEARED` para os destinatarios impactados.
- O frontend usa o evento WebSocket para atualizar imediatamente o bundle de notificacoes, mantendo a mesma notificacao visual ja existente no sistema.
- Quando a aba estiver em segundo plano e o navegador tiver permissao, o frontend dispara tambem uma notificacao nativa usando a API `Notification`.
- A deduplicacao do alerta nativo usa o `eventId` recebido do backend e `localStorage`, evitando o mesmo popup em varias abas do mesmo navegador para o mesmo evento.

## Fluxo Ciclico

1. Cliente cria chamado.
2. Equipe recebe `ticket-assignment`.
3. Equipe responde.
4. Backend oculta a notificacao ativa do ticket e emite `CLEARED`.
5. Cliente envia nova mensagem no mesmo chamado.
6. Backend cria `ticket-reply` novo e emite outro `CREATED`.
7. O ciclo se repete a cada nova mensagem do cliente apos resposta da equipe.

## Arquivos Principais

- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/service/TicketService.java`
- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/service/TicketNotificationRealtimeService.java`
- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/realtime/TicketNotificationRealtimeHandshakeInterceptor.java`
- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/realtime/TicketNotificationRealtimeSessionRegistry.java`
- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/realtime/TicketNotificationRealtimeWebSocketHandler.java`
- `backend/helpdesk/src/main/java/com/helpdesk/helpdesk/config/TicketNotificationRealtimeWebSocketConfig.java`
- `frontend/src/App.jsx`
- `frontend/src/app/api.js`

## Roteiro De Homologacao

### 1. Aba Em Segundo Plano

- Entrar no ChamaQui com um administrador ou funcionario.
- Deixar a aba do ChamaQui em segundo plano.
- Criar um novo chamado por outra sessao.
- Confirmar:
  - contador no titulo da aba foi atualizado;
  - item apareceu na campainha sem recarregar;
  - notificacao nativa apareceu se a permissao do navegador estiver liberada.

### 2. Ciclo Cliente x Equipe No Mesmo Chamado

- Criar um chamado novo.
- Confirmar a aparicao inicial da notificacao.
- Responder como administrador ou funcionario.
- Confirmar que a notificacao daquele ticket sumiu.
- Enviar nova mensagem como cliente no mesmo chamado.
- Confirmar que a notificacao reapareceu para o mesmo ticket.
- Repetir mais uma vez para validar o ciclo.

### 3. Multiplos Funcionarios No Mesmo Tenant

- Abrir duas sessoes diferentes de funcionarios ou administrador + funcionario.
- Criar um chamado atribuido a esse fluxo.
- Confirmar que cada usuario elegivel recebeu apenas um evento novo por item criado.
- Com o mesmo usuario aberto em mais de uma aba, confirmar que o popup nativo do navegador nao duplica para o mesmo `eventId`.

## Observacao De Deploy

- O codigo esta pronto para subir primeiro em homologacao.
- Depois do deploy em homologacao, execute o roteiro acima antes de promover para producao.
