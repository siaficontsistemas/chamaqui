# Registro De Operadores E Integracoes

## Objetivo

Este documento consolida os principais provedores, operadores e componentes de infraestrutura identificados no projeto, com foco em responsabilidades, local de armazenamento e pendencias contratuais ou de governanca.

## Matriz De Operadores E Integracoes

| Operador / Integracao | Funcao | Dados Potencialmente Tratados | Local / Infraestrutura | Responsabilidade Principal | Ponto De Atencao |
| --- | --- | --- | --- | --- | --- |
| PostgreSQL | banco de dados principal do backend | cadastro, chamados, mensagens, notificacoes, auditoria, direitos do titular | ambiente do backend / compose local / producao | armazenamento transacional primario | confirmar backup, criptografia, acesso e politica de restauracao |
| EC2 | hospedagem do backend e do `baileys-service` | trafego operacional, aplicacao, logs, anexos locais, sessao de integracao | AWS EC2 | execucao da aplicacao e servicos auxiliares | validar endurecimento da instancia, acesso SSH e monitoramento |
| PM2 | gerenciamento de processos do backend e `baileys` | metadados operacionais e logs de processo | EC2 | orquestracao dos processos em producao | revisar retencao e acesso aos logs |
| S3 | publicacao do frontend e, por configuracao, logos empresariais | arquivos estaticos e possivelmente logos | AWS S3 | armazenamento de objetos | validar bucket policy, criptografia e ciclo de vida |
| CloudFront | distribuicao do frontend | conteudo estatico e metadados de acesso | AWS CloudFront | entrega de frontend | validar logs, TTLs e restricoes de acesso administrativo |
| SMTP / provedor de email | envio de notificacoes e recuperacao de senha | email, nome, mensagens de notificacao, links/tokens operacionais | provedor externo configurado por `SPRING_MAIL_*` | entrega de email | validar contrato/DPA, local de processamento e politicas do provedor |
| WhatsApp / Baileys | integracao de mensagens e atendimento | telefone, mensagens, anexos, identificadores de conversa | `baileys-service` + infraestrutura WhatsApp | recepcao e roteamento de mensagens para o sistema | validar base legal, sessao autenticada, retencao e responsabilidade conjunta com operacao |
| GitHub Actions | automacao de deploy | artefatos de build, segredos de pipeline e acesso operacional | GitHub + AWS/SSH | esteira de publicacao | revisar escopo de segredos e acesso minimo nas credenciais |
| Disco local de anexos | armazenamento de anexos de chamados | arquivos enviados pelo usuario ou recebidos via WhatsApp | diretório local configurado por `APP_STORAGE_ATTACHMENTS_DIR` | persistencia local dos anexos | validar backup, criptografia, permissao de acesso e expurgo |

## Responsabilidades Internas

- **Controlador / operacao da plataforma**: define finalidade, acesso, retencao, resposta a incidentes, direitos do titular e revisao contratual.
- **Operadores / provedores**: processam dados conforme instrucao, hospedagem, envio de email, distribuicao de frontend e integracoes tecnicas.
- **Administradores de tenant**: operam dados dentro do escopo do proprio ambiente, com dever de acesso minimo e uso adequado.

## Checklist Contratual E De Governanca

Para cada provedor relevante, confirmar:

- existencia de contrato ou termo com clausulas de protecao de dados
- DPA ou aditivo de tratamento quando aplicavel
- local principal de armazenamento e transferencia internacional, se houver
- medidas de seguranca declaradas pelo fornecedor
- regras de subcontratacao
- trilha de acesso administrativo e segregacao de credenciais
- politica de backup, retencao e exclusao

## Status Recomendado De Revisao

| Item | Status Operacional Recomendado |
| --- | --- |
| AWS EC2 / S3 / CloudFront | revisar IAM, criptografia, logs e DPA padrão do provedor |
| SMTP | confirmar provedor efetivo, contrato e local de armazenamento |
| WhatsApp / Baileys | confirmar papel de cada parte, local de sessao, controles de acesso e retenção |
| GitHub Actions | revisar segredos, usuarios com acesso e escopo das credenciais |

## Fontes Tecnicas Do Projeto

- `README.md`
- `.github/workflows/deploy-backend.yml`
- `.github/workflows/deploy-frontend.yml`
- `backend/helpdesk/src/main/resources/application.properties`
- `scripts/deploy/ec2-deploy.sh`
- `baileys-service/src/server.js`

## Revisao

- Versao inicial: `2026-06`
