alter table users
  add column if not exists pre_registered boolean not null default false,
  add column if not exists registration_token_hash varchar(120),
  add column if not exists registration_token_expires_at timestamptz;

-- Contas antigas podem ter compartilhado telefone antes da regra de unicidade.
-- Mantemos o telefone no registro mais utilizado (com histórico de chamados) e,
-- em empate, no cadastro mais antigo. Não removemos nenhuma conta nem chamado;
-- os registros restantes ficam sem telefone para poderem ser corrigidos depois.
with ranked_users as (
  select
    u.id,
    row_number() over (
      partition by u.phone_number
      order by
        (exists (select 1 from tickets t where t.requester_id = u.id or t.assigned_to = u.id)) desc,
        u.created_at asc,
        u.id asc
    ) as phone_rank
  from users u
  where u.phone_number is not null
)
update users u
set phone_number = null
from ranked_users ranked
where ranked.id = u.id
  and ranked.phone_rank > 1;

create unique index if not exists uk_users_phone_number on users(phone_number) where phone_number is not null;
create unique index if not exists uk_users_registration_token_hash on users(registration_token_hash) where registration_token_hash is not null;
