create table company_access_requests (
  id uuid primary key default gen_random_uuid(),
  requester_user_id uuid not null,
  target_company_id uuid not null,
  responded_by_user_id uuid,
  status varchar(20) not null,
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint fk_company_access_requests_requester
    foreign key (requester_user_id) references users (id) on delete cascade,
  constraint fk_company_access_requests_target_company
    foreign key (target_company_id) references users (id) on delete cascade,
  constraint fk_company_access_requests_responded_by
    foreign key (responded_by_user_id) references users (id) on delete set null
);

create index idx_company_access_requests_target_created_at
  on company_access_requests (target_company_id, created_at desc);

create unique index idx_company_access_requests_pending_requester
  on company_access_requests (requester_user_id)
  where status = 'PENDING';
