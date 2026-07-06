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
- Evidencia local apos correcao:
  - `WhatsappServiceTest` confirmou que `77997006654` agora sai como `5577997006654@s.whatsapp.net`.
  - `WhatsappServiceTest` confirmou que destinatarios `@lid` continuam intactos.

## Verification Conclusion
- Hipotese A: rejeitada. O fluxo de notificacao foi executado.
- Hipotese B: rejeitada. A origem usada foi a sessao correta da empresa.
- Hipotese C: confirmada. O destinatario foi normalizado sem DDI `55`.
- Hipotese D: rejeitada. O fluxo real em producao chegou ao envio.
- Hipotese E: rejeitada. Nao houve excecao no backend; o problema estava no destinatario montado.
