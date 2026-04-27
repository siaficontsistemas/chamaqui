alter table users
  add column if not exists company_type varchar(20),
  add column if not exists company_owner_id uuid;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_users_company_owner'
  ) then
    alter table users
      add constraint fk_users_company_owner
      foreign key (company_owner_id) references users (id);
  end if;
end $$;

create index if not exists idx_users_company_owner_id on users (company_owner_id);
create index if not exists idx_users_company_type on users (company_type);

update users
set company_type = 'RESPONDER'
where company_name is not null
  and company_document is not null
  and company_type is null
  and exists (
    select 1
    from user_roles ur
    join roles r on r.id = ur.role_id
    where ur.user_id = users.id
      and r.code = 'ADMIN'
  );
