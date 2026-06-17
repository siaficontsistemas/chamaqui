# Plano De Resposta A Incidente

## Objetivo

Estabelecer o fluxo minimo de resposta a incidentes de seguranca e privacidade no `ChamAqui Helpdesk`, cobrindo deteccao, contencao, analise, comunicacao e criterios de notificacao a ANPD e titulares quando aplicavel.

## O Que E Considerado Incidente

Exemplos:

- acesso indevido a dados pessoais ou operacionais
- vazamento, perda, alteracao ou indisponibilidade indevida de dados
- uso anormal de credenciais, sessoes ou segredos
- exposicao indevida de anexos, mensagens ou historicos
- comprometimento de integracao com email, infraestrutura AWS ou WhatsApp/Baileys

## Fases Do Fluxo

### 1. Deteccao

- registrar horario inicial do evento
- coletar evidencias basicas sem ampliar a exposicao
- identificar fonte do alerta: usuario, log, monitoramento, provedor, time interno ou terceiro

### 2. Classificacao

- classificar severidade preliminar
- identificar quais ambientes foram afetados
- levantar tipos de dados possivelmente envolvidos
- estimar quantidade de titulares, tenants e operadores impactados

### 3. Contencao

- revogar credenciais, sessoes, tokens ou acessos comprometidos
- isolar integrações ou processos afetados quando necessario
- interromper exportacoes, sincronizacoes ou distribuicao do dado comprometido
- preservar evidencias tecnicas para investigacao posterior

### 4. Analise

- determinar causa raiz provavel
- identificar dados efetivamente afetados
- validar periodo de exposicao
- registrar medidas aplicadas e risco residual

### 5. Comunicacao Interna

- acionar responsavel tecnico, gestor operacional e ponto focal de privacidade
- registrar status, escopo e decisoes em linha do tempo
- atualizar a avaliacao de impacto conforme novas evidencias

### 6. Notificacao Externa

Quando houver risco ou dano relevante aos titulares, avaliar:

- notificacao a ANPD dentro de prazo razoavel, com descricao do incidente, categorias de dados, titulares afetados, medidas tecnicas e riscos
- comunicacao aos titulares afetados com linguagem clara, orientacoes praticas e medidas de mitigacao
- acionamento de operadores e fornecedores envolvidos quando fizer parte da cadeia do incidente

### 7. Recuperacao E Aprendizado

- restaurar operacao em ambiente controlado
- rotacionar segredos e credenciais aplicaveis
- revisar controles preventivos, retencao de logs, contratos e procedimentos
- produzir registro final com causa, impacto e acoes corretivas

## Registro Minimo Do Incidente

Cada incidente deve registrar:

- identificador unico
- data e hora de deteccao
- origem do alerta
- sistemas afetados
- categorias de dados envolvidas
- tenants ou empresas afetadas
- titulares potencialmente impactados
- medidas de contencao
- status da analise
- decisao sobre notificacao externa
- data de encerramento e plano corretivo

## Critérios De Escalonamento

Escalonar imediatamente quando houver:

- suspeita de acesso indevido a muitos registros
- vazamento de anexos, mensagens ou dados cadastrais
- comprometimento de credenciais privilegiadas
- incidente com integracao de WhatsApp, email ou infraestrutura cloud
- impacto multitenant

## Controles Complementares

- manter trilha minima de auditoria ativa
- limitar acesso aos registros do incidente ao grupo responsavel
- preservar evidencias sem replicar desnecessariamente dados pessoais
- revisar politicas de backup, restauracao e retencao apos cada incidente relevante

## Revisao

- Versao inicial: `2026-06`
