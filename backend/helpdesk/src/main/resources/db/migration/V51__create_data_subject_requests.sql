create table if not exists data_subject_requests (
  id uuid primary key,
  requester_user_id uuid not null references users (id) on delete cascade,
  tenant_owner_user_id uuid,
  request_type varchar(30) not null,
  status varchar(20) not null,
  requester_full_name varchar(150) not null,
  requester_email varchar(150) not null,
  request_description varchar(4000) not null,
  response_summary varchar(4000),
  internal_notes varchar(4000),
  requested_at timestamptz not null,
  due_at timestamptz not null,
  resolved_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index if not exists idx_data_subject_requests_requester_user_id
  on data_subject_requests (requester_user_id);

create index if not exists idx_data_subject_requests_tenant_owner_user_id
  on data_subject_requests (tenant_owner_user_id);

create index if not exists idx_data_subject_requests_status
  on data_subject_requests (status);
