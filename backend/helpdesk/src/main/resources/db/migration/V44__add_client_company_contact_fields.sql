alter table users
add column if not exists company_contact_email varchar(150);

alter table users
add column if not exists company_contact_phone varchar(30);
