alter table team_invites
  add column invitee_hidden boolean not null default false,
  add column inviter_hidden boolean not null default false;
