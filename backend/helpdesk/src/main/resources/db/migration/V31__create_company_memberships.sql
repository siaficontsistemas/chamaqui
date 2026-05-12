create table if not exists company_memberships (
  id uuid primary key,
  user_id uuid not null,
  company_owner_id uuid not null,
  joined_at timestamptz not null default now(),
  constraint fk_company_memberships_user
    foreign key (user_id) references users (id) on delete cascade,
  constraint fk_company_memberships_company_owner
    foreign key (company_owner_id) references users (id) on delete cascade,
  constraint uk_company_memberships_user_company unique (user_id, company_owner_id)
);

create index if not exists idx_company_memberships_user_id
  on company_memberships (user_id);

create index if not exists idx_company_memberships_company_owner_id
  on company_memberships (company_owner_id);

insert into company_memberships (id, user_id, company_owner_id, joined_at)
select gen_random_uuid(), user_member.id, user_member.company_owner_id, coalesce(user_member.updated_at, user_member.created_at, now())
from users user_member
where user_member.company_owner_id is not null
on conflict (user_id, company_owner_id) do nothing;
