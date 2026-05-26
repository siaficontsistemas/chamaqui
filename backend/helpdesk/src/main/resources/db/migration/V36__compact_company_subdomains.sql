with normalized_companies as (
  select
    id,
    created_at,
    coalesce(nullif(regexp_replace(lower(subdomain), '[^a-z0-9]+', '', 'g'), ''), 'empresa') as base_subdomain
  from companies
),
ranked_companies as (
  select
    id,
    base_subdomain,
    row_number() over (
      partition by base_subdomain
      order by created_at, id
    ) as duplicate_index
  from normalized_companies
),
resolved_subdomains as (
  select
    id,
    case
      when duplicate_index = 1 then left(base_subdomain, 80)
      else left(base_subdomain, 80 - char_length(duplicate_index::text)) || duplicate_index::text
    end as new_subdomain
  from ranked_companies
)
update companies company
set subdomain = resolved_subdomains.new_subdomain,
    updated_at = now()
from resolved_subdomains
where company.id = resolved_subdomains.id
  and company.subdomain <> resolved_subdomains.new_subdomain;
