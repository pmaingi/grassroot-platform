alter table paid_account add column enabled_date_time timestamp;
alter table paid_account add column enabled_by_user bigint;
alter table paid_account add column last_payment_date timestamp;
alter table paid_account add column outstanding_balance bigint; -- as above

-- assumes that in all legacy systems, first user is admin
update paid_account set created_by_user = 1 where created_by_user is null;
update paid_account set enabled_by_user = created_by_user;
update paid_account set enabled_date_time = created_date_time;
update paid_account set disabled_date_time = '2099-12-31 23:59' where disabled_date_time != null; -- in postgresql is not null doesn't work well here

alter table paid_account alter column created_by_user set not null;
alter table paid_account alter column disabled_date_time set not null;
alter table paid_account alter column enabled_date_time set not null;
alter table paid_account alter column enabled_by_user set not null;

alter table paid_account add constraint fk_enabled_by_user foreign key (enabled_by_user) references user_profile;

create table paid_account_billing (
  id  bigserial not null,
  amount_billed bigint not null,
  billed_balance bigint not null,
  billed_period_end timestamp not null,
  billed_period_start timestamp not null,
  created_date_time timestamp not null,
  opening_balance bigint not null,
  statement_date_time timestamp not null,
  uid varchar(255) not null,
  account_id bigint not null,
  primary key (id)
);

alter table paid_account_billing add constraint uk_billing_record_uid unique (uid);
alter table paid_account_billing add constraint fk_billing_record_account foreign key (account_id) references paid_account;

alter table account_log add column reference_amount bigint; -- using big int to store this in cents (i.e., / 100 for humans)