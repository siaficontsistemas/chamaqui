alter table users
  add column company_name varchar(150),
  add column company_document varchar(20);

alter table users
  add constraint uk_users_company_document unique (company_document);
