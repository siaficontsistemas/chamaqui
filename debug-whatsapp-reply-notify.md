# Debug Session: whatsapp-reply-notify [OPEN]

## Sintoma
- Mensagem nova do cliente em chamado aberto via WhatsApp nao volta a notificar no topo.
- Em tentativas recentes, houve regressao em que a mensagem deixou de aparecer no chamado.

## Comportamento Esperado
- Cliente envia nova mensagem no mesmo chamado aberto via WhatsApp.
- A mensagem deve aparecer no historico do chamado.
- A notificacao deve reaparecer para responsavel/admin.

## Hipoteses
1. O webhook recebe a mensagem, mas nao chama `ticketService.addWhatsappMessage()` no ticket esperado.
2. `addWhatsappMessage()` salva a mensagem e depois falha ao criar notificacao, revertendo a transacao.
3. A mensagem e salva, mas a tela do chamado nao recarrega e mascara o resultado real.
4. A notificacao e criada, mas a consulta de notificacoes a exclui por estado do ticket.
5. A conversa ativa do WhatsApp esta vinculada a outro ticket no momento da replica.

## Plano
- Instrumentar somente pontos de entrada e persistencia relacionados ao fluxo WhatsApp e notificacao.
- Reproduzir com logs.
- Analisar evidencia antes de qualquer ajuste de logica.
