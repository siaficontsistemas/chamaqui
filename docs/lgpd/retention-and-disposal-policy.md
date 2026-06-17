# Politica De Retencao E Descarte

## Objetivo

Esta politica define prazos de referencia, criterios de retencao e formas de descarte para os principais conjuntos de dados tratados pelo `ChamAqui Helpdesk`.

## Premissas

- A retencao deve observar necessidade operacional, seguranca, obrigacoes legais, regulatórias e exercicio regular de direitos.
- Sempre que houver necessidade de preservacao por incidente, disputa, auditoria, investigacao ou ordem legal, o descarte deve ser suspenso para os registros afetados.
- O descarte deve priorizar exclusao segura ou anonimização quando a manutencao integral nao for mais necessaria.

## Matriz De Retencao

| Tipo De Dado | Exemplos | Retencao De Referencia | Descarte Esperado | Observacao Tecnica Atual |
| --- | --- | --- | --- | --- |
| Cadastro de usuario | nome, email, CPF, telefone, vinculo empresarial | enquanto a conta estiver ativa e pelo prazo necessario para obrigacoes legais, seguranca e defesa | exclusao logica ou fisica conforme fluxo aprovado | o sistema ja possui exclusao de perfil e empresa |
| Sessoes e autenticacao | sessao web, aceite de documentos, IP, user-agent de aceite | ate perda de finalidade de seguranca e evidenciacao | expurgo ou substituicao por registro minimizado | aceite auditavel passou a ser registrado no backend |
| Tokens de recuperacao e convites | reset de senha, convites administrativos e de equipe | ate expiracao, uso, revogacao ou prazo operacional curto | invalidacao imediata e limpeza periodica | expiracao ja existe para convites e reset; limpeza em lote pode ser operacional |
| Chamados | protocolo, descricao, historico, status, responsavel | pelo prazo necessario ao atendimento, historico operacional e defesa | exclusao logica e, quando autorizado, expurgo controlado | chamados usam `deletedAt` para exclusao logica |
| Mensagens de chamados | conversas entre cliente e equipe | mesmo prazo do chamado associado | exclusao junto ao chamado ou anonimização | seguem o ciclo do ticket |
| Anexos | comprovantes, imagens, documentos, audios, videos | mesmo prazo do chamado associado, salvo obrigacao legal superior | exclusao fisica controlada do arquivo e do registro | exclusao fisica ja ocorre em fluxos de exclusao de ticket/perfil/empresa |
| Notificacoes internas | atribuicao, resposta, calendario, equipe | prazo operacional curto, recomendado ate `180 dias` apos resolucao | exclusao fisica ou limpeza programada | parte das notificacoes ja some por fluxo funcional; expurgo historico ainda deve ser operacionalizado se exigido |
| Convites e solicitacoes de acesso | convites de equipe, convites administrativos, pedidos de acesso | ate resposta, expiracao ou no maximo `180 dias` apos encerramento | exclusao ou arquivamento minimizado | convites possuem expiracao e status |
| Logs tecnicos e auditoria | trilha de auditoria, eventos sensiveis, logs de aplicacao | recomendado entre `180` e `365 dias`, conforme criticidade e capacidade de defesa | expurgo periodico e segregado | trilha minima de auditoria ja existe; politica de rotacao depende da operacao |
| Dados de WhatsApp | identificadores, mensagens, anexos recebidos, estado de sessao | mesmo prazo do chamado quando vinculados ao atendimento; estado de sessao enquanto integracao estiver ativa | exclusao do vinculo operacional e limpeza de credenciais locais quando encerrar uso | `baileys` persiste autenticacao em volume/diretorio dedicado |
| Logos e branding | logos empresariais e arquivos de identidade visual | enquanto a empresa estiver ativa e usar o recurso | exclusao fisica do objeto/arquivo | exclusao gerenciada ja existe para logos |
| Solicitações de direitos do titular | pedido, status, notas e resposta | recomendado `5 anos` para demonstracao de conformidade e defesa | arquivamento controlado ou anonimização quando cabivel | registro interno foi implementado no backend |

## Regras Operacionais

### Chamados, mensagens e anexos

- O chamado deve seguir exclusao logica por padrao quando a operacao ainda puder demandar rastreabilidade.
- Quando houver autorizacao de descarte definitivo, anexos associados devem ser removidos fisicamente.
- Expurgo massivo deve ser precedido de validacao de impacto juridico e operacional.

### Notificacoes, convites e tokens

- Convites expirados ou respondidos nao devem permanecer disponiveis para uso.
- Tokens de recuperacao e aceite devem ter ciclo curto e invalidacao imediata apos uso.
- A operacao deve executar rotina periodica de revisao/expurgo de registros historicos sem finalidade atual.

### Logs e auditoria

- Logs tecnicos nao devem armazenar conteudo excessivo de mensagens ou anexos.
- A trilha de auditoria deve priorizar `quem`, `quando`, `acao`, `tenant` e `alvo`.
- O prazo final de retencao de logs deve ser definido junto ao time juridico e de seguranca.

## Ações Operacionais Recomendadas

- definir job periodico para limpeza de registros historicos sem finalidade atual
- revisar trimestralmente o volume de anexos, logs e sessoes de WhatsApp
- manter checklist de preservacao legal antes de qualquer expurgo definitivo
- registrar toda excecao de retencao ampliada por incidente, auditoria ou disputa

## Revisao

- Versao inicial: `2026-06`
