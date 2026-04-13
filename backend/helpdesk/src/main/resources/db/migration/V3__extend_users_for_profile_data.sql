alter table users
  add column phone_number varchar(30),
  add column document_number varchar(20);

alter table users
  add constraint uk_users_document_number unique (document_number);
