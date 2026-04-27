create table if not exists company_partnerships (
    id uuid primary key,
    requester_company_id uuid not null references users(id),
    target_company_id uuid not null references users(id),
    requested_by_user_id uuid not null references users(id),
    responded_by_user_id uuid references users(id),
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    responded_at timestamptz
);

create index if not exists idx_company_partnerships_requester_company
    on company_partnerships (requester_company_id);

create index if not exists idx_company_partnerships_target_company
    on company_partnerships (target_company_id);

create index if not exists idx_company_partnerships_status
    on company_partnerships (status);
