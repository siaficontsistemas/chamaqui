# ChamAqui Helpdesk

Plataforma de help desk com foco em abertura, acompanhamento e gestão de chamados, incluindo atendimento web, calendário de obrigações, notificações internas, gestão de equipe e integração com WhatsApp.

## Visão Geral

O projeto foi estruturado para atender operações de suporte e atendimento interno/externo com uma interface web moderna e um backend orientado a APIs. A solução combina:

- abertura e acompanhamento de chamados;
- organização por setores;
- gestão de equipe e permissões;
- relatórios operacionais;
- calendário de obrigações com lembretes;
- notificações internas;
- integração com WhatsApp para abertura e continuidade de atendimento.

## Principais Funcionalidades

- Autenticação e cadastro de usuários.
- Perfis com papéis distintos, como administrador, funcionário e usuário.
- Criação, listagem, atualização e fechamento de chamados.
- Conversa por chamado com suporte a anexos.
- Transferência de chamados entre responsáveis.
- Organização por setores.
- Gestão de equipe e convites.
- Solicitações de acesso a empresas.
- Parcerias entre empresas.
- Relatórios.
- Calendário de obrigações com lembretes por CPF.
- Integração com WhatsApp via serviço dedicado.
- Notificações de atribuição, transferência, calendário, equipe e parceria.

## Arquitetura

O repositório é dividido em três blocos principais:

- `frontend`: aplicação React com Vite.
- `backend/helpdesk`: API REST em Spring Boot.
- `baileys-service`: serviço Node.js para integração com WhatsApp.

Também há um ambiente de orquestração com Docker Compose para subir frontend, backend, PostgreSQL e serviço do WhatsApp.

## Stack Tecnológica

### Frontend

- React 19
- Vite
- React Router
- ESLint

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Spring Mail
- Springdoc OpenAPI / Swagger UI

### Integrações e Serviços

- Node.js
- Express
- Baileys
- QR Code para autenticação do WhatsApp

## Estrutura do Repositório

```text
helpdesk/
|-- backend/
|   `-- helpdesk/
|       |-- src/
|       |-- pom.xml
|       `-- Dockerfile
|-- frontend/
|   |-- src/
|   |-- package.json
|   `-- Dockerfile
|-- baileys-service/
|   |-- src/
|   |-- package.json
|   `-- Dockerfile
|-- docker-compose.yml
`-- README.md
```

## Requisitos

- Docker e Docker Compose

ou, para execução local sem containers:

- Java 21
- Maven Wrapper
- Node.js
- npm
- PostgreSQL

## Execução Rápida com Docker

Na raiz do projeto:

```bash
docker compose up --build
```

Serviços expostos por padrão:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:4200`
- PostgreSQL: `localhost:5432`
- Serviço do WhatsApp: `http://localhost:21465`

## Execução Local por Serviço

### 1. Backend

```bash
cd backend/helpdesk
./mvnw spring-boot:run
```

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

### 3. Serviço do WhatsApp

```bash
cd baileys-service
npm install
npm start
```

## Banco de Dados

O backend utiliza PostgreSQL e migrações versionadas com Flyway.

Ao iniciar a aplicação com o banco configurado corretamente, as migrações são aplicadas automaticamente.

## Documentação da API

O backend expõe documentação Swagger UI em:

- [http://localhost:4200/swagger-ui.html](http://localhost:4200/swagger-ui.html)

## Documentação LGPD

Artefatos operacionais desta frente ficam em:

- `docs/lgpd/data-processing-register.md`
- `docs/lgpd/data-subject-rights-process.md`
- `docs/lgpd/retention-and-disposal-policy.md`
- `docs/lgpd/operators-and-integrations-register.md`
- `docs/lgpd/incident-response-plan.md`
- `docs/lgpd/internal-training-and-process.md`

Documentos legais publicados no fluxo público da aplicação:

- `/termos-de-uso`
- `/politica-de-privacidade`

## Variáveis de Ambiente

### Backend

Principais variáveis suportadas:

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_SMTP_AUTH`
- `SPRING_MAIL_SMTP_STARTTLS_ENABLE`
- `SPRING_MAIL_SMTP_SSL_ENABLE`
- `APP_MAIL_FROM`
- `APP_MAIL_TICKET_CLOSURE_FROM`
- `APP_MAIL_COMPANY_INVITE_FROM`
- `APP_FRONTEND_BASE_URL`
- `APP_WHATSAPP_BASE_URL`
- `APP_WHATSAPP_API_KEY`
- `APP_WHATSAPP_WEBHOOK_URL`
- `APP_STORAGE_ATTACHMENTS_DIR`
- `APP_STORAGE_ATTACHMENTS_LEGACY_DIRS`

Configuração recomendada para emails da plataforma:

- `APP_MAIL_FROM=chamaqui@siaficont.com.br`
- `APP_MAIL_TICKET_CLOSURE_FROM=chamaqui@siaficont.com.br`
- `APP_MAIL_COMPANY_INVITE_FROM=chamaqui@siaficont.com.br`
- `APP_FRONTEND_BASE_URL=https://chamaqui.app.br`
- `SPRING_MAIL_SMTP_SSL_ENABLE=false`

Observação importante:

- o SMTP pode continuar autenticando com `sistemas@siaficont.com.br` se esse for o usuário real da conta;
- o alias `chamaqui@siaficont.com.br` precisa estar autorizado pelo provedor para ser usado no campo remetente (`From`).

### WhatsApp / Baileys

- `PORT`
- `BAILEYS_API_KEY`
- `APP_WHATSAPP_WEBHOOK_URL`
- `BAILEYS_LOG_LEVEL`

## Armazenamento de Anexos

Os anexos dos chamados são armazenados em disco, fora do banco de dados.

Configuração atual:

- diretório principal padrão: `${user.home}/.helpdesk/uploads/ticket-attachments`
- diretórios legados de leitura: `${user.dir}/uploads/ticket-attachments`

Para ambientes de produção, recomenda-se definir explicitamente:

- `APP_STORAGE_ATTACHMENTS_DIR`
- `APP_STORAGE_ATTACHMENTS_LEGACY_DIRS`

Isso evita perda de referência a arquivos após mudança de pasta de execução, rebuild ou troca de ambiente.

## Docker Compose

O `docker-compose.yml` atualmente orquestra:

- `frontend`
- `backend`
- `baileys`
- `postgres`

Volumes nomeados já existentes no compose:

- `postgres_data`
- `baileys_auth`

## Módulos Funcionais do Frontend

As rotas mapeadas no frontend incluem:

- `login`
- `register`
- `tickets`
- `tickets/all`
- `tickets/open`
- `tickets/closed`
- `tickets/new`
- `calendar`
- `reports`
- `my-data`
- `team`
- `sectors/new`

## Serviços Principais do Backend

Os serviços centrais incluem:

- `AuthService`
- `TicketService`
- `WhatsappService`
- `WhatsappWebhookService`
- `CalendarService`
- `NotificationService`
- `TeamService`
- `SectorService`
- `ProfileService`
- `ReportService`
- `CompanyPartnershipService`
- `CompanyAccessRequestService`

## Fluxo Geral da Solução

1. O usuário acessa o frontend e autentica na aplicação.
2. O frontend consome a API REST do backend.
3. O backend persiste dados no PostgreSQL e executa regras de negócio.
4. O serviço de WhatsApp recebe eventos e se comunica com o backend via webhook.
5. O backend dispara notificações, salva anexos e mantém o histórico dos atendimentos.

## Boas Práticas para Ambiente de Produção

- Definir variáveis de ambiente explicitamente.
- Persistir anexos em diretório estável e externo ao container.
- Persistir dados do PostgreSQL em volume dedicado.
- Persistir autenticação do WhatsApp em volume.
- Configurar servidor SMTP válido para notificações por e-mail.
- Restringir exposição pública de portas quando necessário.

## Deploy Automatizado

O repositório inclui dois workflows separados, alinhados com a arquitetura real de produção:

- `frontend` publicado em `S3`
- cache do frontend invalidado no `CloudFront`
- `backend` em `PM2` na `EC2` com `java -jar`
- `baileys-service` em `PM2` na `EC2`
- deploy feito por `GitHub Actions -> AWS + SSH -> EC2`

### Workflows

- [deploy-backend.yml](file:///home/kauan_rubem/helpdesk/.github/workflows/deploy-backend.yml): faz deploy de `backend + baileys` na `EC2` via `PM2`
- [deploy-frontend.yml](file:///home/kauan_rubem/helpdesk/.github/workflows/deploy-frontend.yml): faz deploy do `frontend` em `S3 + CloudFront`

### O que o workflow de backend faz

1. Faz build do `backend` com Maven.
2. Empacota o `jar` do backend e o código do `baileys-service`.
3. Envia o bundle da aplicação para a EC2 por `scp`.
4. Executa o script [ec2-deploy.sh](file:///home/kauan_rubem/helpdesk/scripts/deploy/ec2-deploy.sh) no servidor.
5. Atualiza o `app.jar`, roda `npm ci --omit=dev` no `baileys-service` e reinicia os processos no `PM2`.

### O que o workflow de frontend faz

1. Faz build do `frontend` com Vite.
2. Publica `frontend/dist` no bucket S3 configurado.
3. Invalida o cache da distribuição CloudFront.

### Secrets obrigatórios no GitHub

Cadastre estes `Secrets and variables -> Actions -> Secrets`:

- `EC2_SSH_PRIVATE_KEY`: chave privada usada no acesso SSH
- `AWS_ACCESS_KEY_ID`: credencial AWS com permissão de publicar no S3 e invalidar CloudFront
- `AWS_SECRET_ACCESS_KEY`: segredo da credencial AWS

### Variables recomendadas no GitHub

Cadastre estes `Repository variables`:

- `AWS_REGION`: região AWS do bucket S3 do frontend
- `FRONTEND_S3_BUCKET`: nome do bucket onde o `frontend/dist` será publicado
- `CLOUDFRONT_DISTRIBUTION_ID`: ID da distribuição CloudFront, por exemplo `E3AVSEKD8ZJQTP`
- `VITE_API_BASE_URL`: URL pública da API usada no build do frontend, por exemplo `https://api.chamaqui.app.br`
- `EC2_SSH_HOST`: host ou IP público da instância, por exemplo `54.160.83.203`
- `EC2_SSH_USER`: usuário SSH da instância, por exemplo `ec2-user`
- `EC2_SSH_PORT`: porta SSH, default `22`
- `DEPLOY_ROOT`: base temporária do deploy, default `/home/ec2-user/deploy/chamaqui`
- `BACKEND_JAR_PATH`: caminho do `jar` usado pelo `PM2`, default `/home/ec2-user/app.jar`
- `BAILEYS_APP_DIR`: pasta do `baileys-service`, default `/home/ec2-user/baileys-service`
- `PM2_BACKEND_APP_NAME`: nome do processo backend no `PM2`, default `chamaqui-backend`
- `PM2_BAILEYS_APP_NAME`: nome do processo do WhatsApp no `PM2`, default `chamaqui-baileys`
- `RESTART_BAILEYS`: `true` ou `false`, default `false`

### Fluxo de uso

- faça `push` na branch `main`
- ou execute manualmente em `Actions -> Deploy Backend`
- ou execute manualmente em `Actions -> Deploy Frontend`

### Gatilhos automáticos

- `Deploy Backend` roda quando há mudanças em `backend/helpdesk`, `baileys-service`, `scripts/deploy` ou no próprio workflow
- `Deploy Frontend` roda quando há mudanças em `frontend` ou no próprio workflow

### Pré-requisitos na EC2

- `pm2` instalado e com os processos já existentes
- `npm` disponível para reinstalar dependências do `baileys-service`
- permissão do usuário SSH para escrever nos caminhos configurados

### Observação importante

Esses workflows mantêm o formato atual do deploy. Eles não migram sua infraestrutura para Docker, ECS ou systemd; apenas automatizam o processo atual, separando corretamente o frontend em `S3 + CloudFront` e o backend/WhatsApp em `EC2 + PM2`.

Como arquivos `.env.*` não sobem para o repositório, a URL da API do frontend de produção deve ser fornecida pelo workflow via `VITE_API_BASE_URL`.

## Comandos Úteis

### Build do frontend

```bash
cd frontend
npm run build
```

### Compilação do backend

```bash
cd backend/helpdesk
./mvnw -q -DskipTests compile
```

### Subir tudo com rebuild

```bash
docker compose up --build
```

## Situação Atual do Projeto

O projeto já contempla fluxos relevantes de operação, incluindo:

- múltiplos chamados em aberto no WhatsApp com seleção de contexto;
- lembretes automáticos no fluxo de atendimento via WhatsApp;
- paginação nas listagens de chamados;
- calendário com múltiplos destinatários por CPF;
- notificações internas e fluxos administrativos.

## Licenciamento

Este repositório não possui licença definida no momento.

## Contato e Manutenção

Se este projeto for usado internamente pela equipe, recomenda-se manter este `README` atualizado sempre que houver mudanças em:

- infraestrutura;
- variáveis de ambiente;
- arquitetura;
- fluxos de autenticação;
- integração com WhatsApp;
- armazenamento de anexos.
