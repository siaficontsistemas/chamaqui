# Inventario De Tratamento De Dados

## Objetivo

Este documento formaliza, em nivel tecnico-operacional, as principais categorias de dados tratadas pela plataforma `ChamAqui Helpdesk`, suas finalidades, bases legais mais provaveis, compartilhamentos esperados e prazos de retencao de referencia.

## Matriz De Dados

| Categoria | Exemplos | Finalidade Principal | Base Legal Mais Provavel | Compartilhamentos | Retencao De Referencia |
| --- | --- | --- | --- | --- | --- |
| Identificacao cadastral | nome, email, CPF | criar conta, identificar usuario, prevenir duplicidade e fraudes | execucao de contrato, procedimentos preliminares, exercicio regular de direitos | administradores e servicos internos autorizados | enquanto a conta estiver ativa e pelo prazo necessario para obrigacoes legais e defesa |
| Contato | telefone, email, contato da empresa | comunicacao operacional, notificacoes, recuperacao de acesso, onboarding | execucao de contrato, interesse legitimo de comunicacao e seguranca | provedores de email, administradores autorizados, empresas vinculadas ao atendimento | enquanto houver relacao ativa ou necessidade comprovavel de contato |
| Dados empresariais | nome da empresa, CNPJ, tipo da empresa, vinculos societarios/operacionais | estruturar tenants, convites, relacoes entre empresas e autorizacoes | execucao de contrato, obrigacao legal, exercicio regular de direitos | empresa responsavel pelo tenant, empresas vinculadas ao chamado, plataforma | enquanto durar o vinculo contratual e pelo prazo legal aplicavel |
| Autenticacao e seguranca | hash de senha, sessao, IP, user-agent, aceite de documentos legais | autenticar acesso, registrar aceite, investigar incidentes e demonstrar conformidade | execucao de contrato, interesse legitimo de seguranca, obrigacao legal | operacao interna e auditoria autorizada | conforme necessidade de seguranca e evidenciacao |
| Operacao de chamados | protocolo, titulo, descricao, setor, responsavel, status, historico de movimentacao | prestar o servico de helpdesk, organizar fila e historico de atendimento | execucao de contrato e exercicio regular de direitos | usuarios autorizados da mesma empresa/tenant e parceiros do fluxo do chamado | enquanto necessario ao atendimento e historico operacional |
| Conteudo de mensagens | mensagens do cliente, mensagens da equipe, respostas via interface e WhatsApp | permitir conversacao e acompanhamento do caso | execucao de contrato | usuarios autorizados no chamado e integracoes operacionais necessarias | enquanto houver necessidade operacional, legal ou probatoria |
| Anexos | arquivos enviados no ticket, imagens, documentos, comprovantes | instruir e resolver o chamado | execucao de contrato, exercicio regular de direitos | usuarios autorizados no chamado e infraestrutura de armazenamento | pelo prazo necessario ao atendimento e obrigacoes legais |
| Integracao WhatsApp | identificador de transporte, mensagens, estado da conversa | roteamento de conversas e continuidade do atendimento | execucao de contrato e interesse legitimo operacional | servico de integracao WhatsApp e usuarios autorizados | enquanto a conversa estiver vinculada ao historico do atendimento |
| Auditoria e trilha operacional | quem acessou, quando, tenant, acao executada, alvo da acao | rastreabilidade, seguranca, resposta a incidentes e demonstracao de conformidade | interesse legitimo de seguranca, exercicio regular de direitos e obrigacao legal quando aplicavel | equipes autorizadas de auditoria e seguranca | conforme politica interna de seguranca e necessidade de defesa |

## Observacoes Operacionais

- Bases legais podem variar por fluxo especifico; a tabela acima cobre o enquadramento tecnico predominante do sistema.
- Dados sensiveis em sentido estrito nao devem ser solicitados pela aplicacao fora de necessidade juridica e operacional claramente justificada.
- Compartilhamentos devem sempre respeitar o principio da necessidade, segregacao por tenant e perfil de acesso.

## Revisao

- Versao inicial: `2026-06`
- Revisao recomendada: a cada mudanca relevante de fluxo, integracao, retencao ou perfil de acesso
