create table if not exists companies (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null,
  company_name varchar(150) not null,
  company_document varchar(20) not null,
  company_type varchar(20) not null,
  subdomain varchar(80) not null,
  schema_name varchar(80) not null,
  logo_url varchar(500),
  login_logo_url varchar(500),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint fk_companies_owner_user
    foreign key (owner_user_id) references users (id) on delete cascade,
  constraint uk_companies_owner_user unique (owner_user_id),
  constraint uk_companies_company_document unique (company_document),
  constraint uk_companies_subdomain unique (subdomain),
  constraint uk_companies_schema_name unique (schema_name),
  constraint ck_companies_company_type
    check (company_type in ('REQUESTER', 'RESPONDER'))
);

create index if not exists idx_companies_subdomain
  on companies (subdomain);

create index if not exists idx_companies_active_subdomain
  on companies (active, subdomain);

create trigger trg_companies_set_updated_at
before update on companies
for each row
execute function set_updated_at();

with admin_candidates as (
  select
    user_admin.id as owner_user_id,
    btrim(user_admin.company_name) as company_name,
    regexp_replace(coalesce(user_admin.company_document, ''), '\D', '', 'g') as company_document,
    user_admin.company_type::text as company_type,
    coalesce(user_admin.created_at, now()) as created_at,
    nullif(
      trim(both '-' from regexp_replace(lower(btrim(user_admin.company_name)), '[^a-z0-9]+', '-', 'g')),
      ''
    ) as base_subdomain
  from users user_admin
  join user_roles user_role on user_role.user_id = user_admin.id
  join roles role on role.id = user_role.role_id
  where role.code = 'ADMIN'
    and user_admin.company_name is not null
    and btrim(user_admin.company_name) <> ''
    and user_admin.company_document is not null
    and regexp_replace(user_admin.company_document, '\D', '', 'g') <> ''
    and user_admin.company_type is not null
),
prepared_candidates as (
  select
    owner_user_id,
    company_name,
    company_document,
    company_type,
    created_at,
    coalesce(base_subdomain, 'empresa') as base_subdomain,
    row_number() over (
      partition by coalesce(base_subdomain, 'empresa')
      order by created_at, owner_user_id
    ) as duplicate_index
  from admin_candidates
),
inserted_companies as (
  insert into companies (
    owner_user_id,
    company_name,
    company_document,
    company_type,
    subdomain,
    schema_name
  )
  select
    owner_user_id,
    company_name,
    company_document,
    company_type,
    case
      when duplicate_index = 1 then left(base_subdomain, 80)
      else left(base_subdomain || '-' || duplicate_index, 80)
    end as subdomain,
    left(
      'tenant_' || replace(
        case
          when duplicate_index = 1 then base_subdomain
          else base_subdomain || '-' || duplicate_index
        end,
        '-',
        '_'
      ),
      80
    ) as schema_name
  from prepared_candidates
  on conflict (owner_user_id) do update
    set company_name = excluded.company_name,
        company_document = excluded.company_document,
        company_type = excluded.company_type,
        updated_at = now()
  returning schema_name
)
select 1;

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from companies
  loop
    execute format('create schema if not exists %I', company_record.schema_name);
  end loop;
end $$;
