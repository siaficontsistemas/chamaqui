insert into roles (code, name)
values
  ('ADMIN', 'Administrador'),
  ('EMPLOYEE', 'Funcionário'),
  ('USER', 'Usuário');

insert into ticket_statuses (code, name, sort_order, is_terminal)
values
  ('OPEN', 'Aberto', 1, false),
  ('IN_PROGRESS', 'Em andamento', 2, false),
  ('WAITING_CUSTOMER', 'Aguardando cliente', 3, false),
  ('RESOLVED', 'Resolvido', 4, false),
  ('CLOSED', 'Fechado', 5, true);

insert into ticket_priorities (code, name, sort_order)
values
  ('LOW', 'Baixa', 1),
  ('MEDIUM', 'Média', 2),
  ('HIGH', 'Alta', 3),
  ('URGENT', 'Urgente', 4);
