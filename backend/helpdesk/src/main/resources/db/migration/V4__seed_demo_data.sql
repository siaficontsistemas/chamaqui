insert into users (
  id,
  full_name,
  email,
  password_hash,
  status,
  is_email_verified,
  phone_number,
  document_number
)
values
  (
    '11111111-1111-1111-1111-111111111111',
    'Administrador Helpdesk',
    'admin@helpdesk.local',
    '$2b$12$tg9kxn2v6Kgb8GqSi/BfMe3gDF7WeIJfIogVOTg/T3x.M.00I1BdK',
    'ACTIVE',
    true,
    '(77) 99999-0001',
    '00000000001'
  ),
  (
    '22222222-2222-2222-2222-222222222222',
    'Renata Alves',
    'renata@helpdesk.local',
    '$2b$12$tg9kxn2v6Kgb8GqSi/BfMe3gDF7WeIJfIogVOTg/T3x.M.00I1BdK',
    'ACTIVE',
    true,
    '(77) 99999-0002',
    '00000000002'
  ),
  (
    '33333333-3333-3333-3333-333333333333',
    'João Silva',
    'joao@helpdesk.local',
    '$2b$12$tg9kxn2v6Kgb8GqSi/BfMe3gDF7WeIJfIogVOTg/T3x.M.00I1BdK',
    'ACTIVE',
    true,
    '(77) 99999-0003',
    '00000000003'
  );

insert into user_roles (user_id, role_id)
select '11111111-1111-1111-1111-111111111111', id
from roles
where code = 'ADMIN';

insert into user_roles (user_id, role_id)
select '22222222-2222-2222-2222-222222222222', id
from roles
where code = 'EMPLOYEE';

insert into user_roles (user_id, role_id)
select '33333333-3333-3333-3333-333333333333', id
from roles
where code = 'USER';

insert into sectors (id, name, slug, description, created_by)
values
  (
    '44444444-4444-4444-4444-444444444444',
    'Financeiro',
    'financeiro',
    'Atendimento a pagamentos, notas e demandas financeiras.',
    '11111111-1111-1111-1111-111111111111'
  ),
  (
    '55555555-5555-5555-5555-555555555555',
    'Tecnologia',
    'tecnologia',
    'Suporte técnico, sistemas internos e infraestrutura.',
    '11111111-1111-1111-1111-111111111111'
  );

insert into sector_members (id, sector_id, user_id, assigned_by)
values
  (
    '66666666-6666-6666-6666-666666666661',
    '44444444-4444-4444-4444-444444444444',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111'
  ),
  (
    '66666666-6666-6666-6666-666666666662',
    '55555555-5555-5555-5555-555555555555',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111'
  );

insert into team_invites (
  id,
  email,
  invited_name,
  invited_by,
  token_hash,
  status,
  expires_at
)
values
  (
    '77777777-7777-7777-7777-777777777777',
    'paulo@helpdesk.local',
    'Paulo Nunes',
    '11111111-1111-1111-1111-111111111111',
    'invite-paulo-nunes',
    'PENDING',
    now() + interval '7 days'
  );

insert into team_invite_sectors (invite_id, sector_id)
values
  (
    '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555'
  );

insert into tickets (
  id,
  protocol,
  title,
  description,
  requester_id,
  assigned_to,
  sector_id,
  status_id,
  priority_id
)
select
  '88888888-8888-8888-8888-888888888881',
  'HD-2026-0001',
  'Erro ao acessar relatório mensal',
  'Usuário relata falha ao abrir o relatório mensal no portal administrativo.',
  '33333333-3333-3333-3333-333333333333',
  '22222222-2222-2222-2222-222222222222',
  '55555555-5555-5555-5555-555555555555',
  ts.id,
  tp.id
from ticket_statuses ts
cross join ticket_priorities tp
where ts.code = 'OPEN'
  and tp.code = 'HIGH';

insert into tickets (
  id,
  protocol,
  title,
  description,
  requester_id,
  assigned_to,
  sector_id,
  status_id,
  priority_id,
  first_response_at
)
select
  '88888888-8888-8888-8888-888888888882',
  'HD-2026-0002',
  'Atualização cadastral pendente',
  'Solicitação para atualização de dados financeiros do fornecedor.',
  '33333333-3333-3333-3333-333333333333',
  '22222222-2222-2222-2222-222222222222',
  '44444444-4444-4444-4444-444444444444',
  ts.id,
  tp.id,
  now()
from ticket_statuses ts
cross join ticket_priorities tp
where ts.code = 'IN_PROGRESS'
  and tp.code = 'MEDIUM';

insert into tickets (
  id,
  protocol,
  title,
  description,
  requester_id,
  assigned_to,
  sector_id,
  status_id,
  priority_id,
  opened_at,
  first_response_at,
  resolved_at,
  closed_at
)
select
  '88888888-8888-8888-8888-888888888883',
  'HD-2026-0003',
  'Reset de senha concluído',
  'Chamado resolvido com sucesso após redefinição de senha e validação do acesso.',
  '33333333-3333-3333-3333-333333333333',
  '22222222-2222-2222-2222-222222222222',
  '55555555-5555-5555-5555-555555555555',
  ts.id,
  tp.id,
  now() - interval '3 days',
  now() - interval '2 days',
  now() - interval '1 day',
  now() - interval '1 day'
from ticket_statuses ts
cross join ticket_priorities tp
where ts.code = 'CLOSED'
  and tp.code = 'LOW';
