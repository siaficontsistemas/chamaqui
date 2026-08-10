alter table users
  add column if not exists privacy_policy_accepted_at timestamptz,
  add column if not exists privacy_policy_version varchar(40);

create table if not exists legal_acceptances (
  id uuid primary key,
  user_id uuid not null references users (id) on delete cascade,
  document_type varchar(30) not null,
  version varchar(40) not null,
  accepted_at timestamptz not null,
  evidence_ip varchar(80),
  evidence_user_agent varchar(255),
  source varchar(40) not null,
  created_at timestamptz not null
);

create index if not exists idx_legal_acceptances_user_id on legal_acceptances (user_id);
create index if not exists idx_legal_acceptances_document_type on legal_acceptances (document_type);
