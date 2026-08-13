create table if not exists web_push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  endpoint text not null,
  p256dh text not null,
  auth_secret text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_web_push_subscription_endpoint unique (endpoint),
  constraint fk_web_push_subscriptions_user foreign key (user_id) references users (id) on delete cascade
);

alter table web_push_subscriptions
  add column if not exists expiration_time double precision;

create index if not exists idx_web_push_subscriptions_user_id on web_push_subscriptions (user_id);

do $$
declare
  company_record record;
begin
  for company_record in select schema_name from companies loop
    execute format(
      'create table if not exists %I.web_push_subscriptions (like public.web_push_subscriptions including all)',
      company_record.schema_name
    );
  end loop;
end $$;
