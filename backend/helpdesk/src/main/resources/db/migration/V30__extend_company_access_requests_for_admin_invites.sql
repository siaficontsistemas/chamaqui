alter table company_access_requests
  alter column requester_user_id drop not null;

alter table company_access_requests
  add column if not exists requester_name varchar(150),
  add column if not exists requester_email varchar(150),
  add column if not exists requester_document_number varchar(20),
  add column if not exists request_type varchar(20) not null default 'USER_REQUEST',
  add column if not exists invite_token_hash varchar(120),
  add column if not exists expires_at timestamptz;

update company_access_requests request
set requester_name = users.full_name,
    requester_email = users.email,
    requester_document_number = users.document_number
from users
where request.requester_user_id = users.id
  and (
    request.requester_name is null
    or request.requester_email is null
    or request.requester_document_number is null
  );

drop index if exists idx_company_access_requests_pending_requester;

create unique index if not exists idx_company_access_requests_pending_requester
  on company_access_requests (requester_user_id)
  where requester_user_id is not null
    and request_type = 'USER_REQUEST'
    and status = 'PENDING';

create index if not exists idx_company_access_requests_requester_pending
  on company_access_requests (requester_user_id, created_at desc)
  where requester_user_id is not null
    and request_type = 'ADMIN_INVITE'
    and status = 'PENDING';

create index if not exists idx_company_access_requests_token_pending
  on company_access_requests (invite_token_hash)
  where invite_token_hash is not null
    and request_type = 'ADMIN_INVITE'
    and status = 'PENDING';
