
# Debug Session: assignee-whatsapp-notify
- **Status**: [OPEN]
- **Issue**: Mensagem automatica de novo chamado nao chega ao funcionario responsavel
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-assignee-whatsapp-notify.ndjson

## Reproduction Steps
1. Abrir um novo chamado com um funcionario responsavel definido.
2. Aguardar o disparo automatico do WhatsApp para o responsavel.
3. Verificar se a mensagem chega ao numero/WhatsApp configurado no usuario responsavel.

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | O metodo de notificacao e chamado, mas sai antes do envio porque o responsavel nao possui destinatario valido | High | Low | Pending |
| B | O envio usa a empresa/origem errada no `whatsappService.sendMessage(...)` | High | Low | Pending |
| C | O destinatario e normalizado em formato que o integrador nao aceita | Medium | Low | Pending |
| D | O fluxo real de criacao nao esta chamando a notificacao no ambiente testado | Medium | Low | Pending |
| E | O `whatsappService.sendMessage(...)` falha em runtime e a excecao fica apenas no backend | High | Low | Pending |

## Log Evidence
- Evidencia de producao:
  - `Envio WhatsApp executado ... recipient=77997006654@s.whatsapp.net, bodyPreview=Voce recebeu um novo chamado no ChamaQui da empresa Siaficont Sistemas.`
  - O envio do menu normal do solicitante usa `recipient=209607456719099@lid`, enquanto a notificacao do responsavel saiu por telefone puro sem DDI.
- Evidencia de producao apos deploy:
  - O fluxo mais recente ficou em `bodyPreview=Chamado para *KAUAN RUBEM FAUSTO MATOS*.` e nao mostrou `Chamado aberto.` nem `Voce recebeu um novo chamado...`.
  - Isso indica que a reproducao mais recente nao chegou ate a conclusao da abertura do chamado, entao nao houve tentativa de notificacao ao responsavel nesse ciclo.
- Evidencia de producao apos nova reproducao completa:
  - O webhook registrou `phone=5577997006654@s.whatsapp.net`, `transportId=5577997006654@s.whatsapp.net` e `fromMe=true`.
  - O `rawPayloadPreview` mostrou o texto completo: `Voce recebeu um novo chamado no ChamaQui da empresa Siaficont Sistemas...`.
  - Isso comprova que, apos a correcao, a integracao passou a montar o destinatario com DDI `55` e o Baileys registrou a mensagem automatica saindo para o responsavel.
- Evidencia local apos correcao:
  - `WhatsappServiceTest` confirmou que `77997006654` agora sai como `5577997006654@s.whatsapp.net`.
  - `WhatsappServiceTest` confirmou que destinatarios `@lid` continuam intactos.

## Verification Conclusion
- Hipotese A: rejeitada. O fluxo de notificacao foi executado.
- Hipotese B: rejeitada. A origem usada foi a sessao correta da empresa.
- Hipotese C: confirmada. O destinatario foi normalizado sem DDI `55`.
- Hipotese D: rejeitada. O fluxo real em producao chegou ao envio.
- Hipotese E: rejeitada. Nao houve excecao no backend; o problema estava no destinatario montado.
- Validacao pos-correcao: confirmada em runtime. A mensagem automatica passou a sair com o destinatario corrigido.
