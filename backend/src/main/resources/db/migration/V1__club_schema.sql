-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
 zone varchar(80) NOT NULL,
 enabled boolean NOT NULL
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(6000) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);






CREATE TABLE member (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(60) NOT NULL,
 name varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 contact_note varchar(300) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 UNIQUE(code),
 FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE coach (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(60) NOT NULL,
 name varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 specialty varchar(300) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 UNIQUE(code),
 FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE account (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 username varchar(60) NOT NULL,
 display_name varchar(120) NOT NULL,
 password_hash varchar(100) NOT NULL,
 role_id bigint NOT NULL,
 department_id bigint NOT NULL,
 member_id bigint NULL,
 coach_id bigint NULL,
 enabled boolean NOT NULL,
 UNIQUE(username),
 UNIQUE(member_id),
 UNIQUE(coach_id),
 FOREIGN KEY(role_id) REFERENCES access_role(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(member_id) REFERENCES member(id),
 FOREIGN KEY(coach_id) REFERENCES coach(id),
 CHECK(member_id IS NULL OR coach_id IS NULL)
);

CREATE TABLE room (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 name varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 capacity int NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 CHECK(capacity BETWEEN 1 AND 100),
 UNIQUE(department_id,name)
);

CREATE TABLE course (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 name varchar(160) NOT NULL,
 name_en varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 category_id bigint NOT NULL,
 duration_minutes int NOT NULL,
 capacity int NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(category_id) REFERENCES dictionary_entry(id),
 CHECK(capacity BETWEEN 1 AND 100),
 CHECK(duration_minutes BETWEEN 5 AND 480)
);

CREATE TABLE membership_plan (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 name varchar(160) NOT NULL,
 name_en varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 mode varchar(20) NOT NULL,
 credits int NOT NULL,
 valid_days int NOT NULL,
 price decimal(18,2) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 CHECK(mode IN ('CREDITS','PERIOD')),
 CHECK(price > 0 AND price <= 999999.99),
 CHECK(valid_days BETWEEN 1 AND 730),
 CHECK((mode='CREDITS' AND credits BETWEEN 1 AND 9999) OR (mode='PERIOD' AND credits=0))
);

CREATE TABLE club_pass (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL,
 department_id bigint NOT NULL,
 member_id bigint NOT NULL,
 plan_id bigint NOT NULL,
 plan_name varchar(160) NOT NULL,
 plan_name_en varchar(160) NOT NULL,
 mode varchar(20) NOT NULL,
 credits int NOT NULL,
 held int NOT NULL,
 freeze_days int NOT NULL,
 used int NOT NULL,
 price decimal(18,2) NOT NULL,
 currency varchar(3) NOT NULL,
 starts_on date NOT NULL,
 ends_on date NOT NULL,
 freeze_started date NULL,
 freeze_until date NULL,
 state varchar(20) NOT NULL,
 note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 revision bigint NOT NULL,
 UNIQUE(reference),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(member_id) REFERENCES member(id),
 FOREIGN KEY(plan_id) REFERENCES membership_plan(id),
 CHECK(held >= 0 AND used >= 0),
 CHECK(mode='PERIOD' OR held + used <= credits),
 CHECK(ends_on >= starts_on)
);

CREATE TABLE club_session (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 course_id bigint NOT NULL,
 coach_id bigint NOT NULL,
 room_id bigint NOT NULL,
 name varchar(160) NOT NULL,
 name_en varchar(160) NOT NULL,
 starts_at timestamp(6) NOT NULL,
 ends_at timestamp(6) NOT NULL,
 capacity int NOT NULL,
 cancel_hours int NOT NULL,
 state varchar(20) NOT NULL,
 note varchar(1000) NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(course_id) REFERENCES course(id),
 FOREIGN KEY(coach_id) REFERENCES coach(id),
 FOREIGN KEY(room_id) REFERENCES room(id),
 CHECK(ends_at > starts_at),
 CHECK(capacity BETWEEN 1 AND 100),
 CHECK(cancel_hours BETWEEN 0 AND 168),
 INDEX ix_session_branch_date(department_id,starts_at)
);

CREATE TABLE reservation (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 member_id bigint NOT NULL,
 session_id bigint NOT NULL,
 pass_id bigint NOT NULL,
 state varchar(20) NOT NULL,
 note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 updated_at timestamp(6) NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(member_id) REFERENCES member(id),
 FOREIGN KEY(session_id) REFERENCES club_session(id),
 FOREIGN KEY(pass_id) REFERENCES club_pass(id),
 UNIQUE(member_id,session_id),
 INDEX ix_reservation_session_state(session_id,state)
);

CREATE TABLE credit_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 pass_id bigint NOT NULL,
 reservation_id bigint NULL,
 kind varchar(40) NOT NULL,
 total_delta int NOT NULL,
 held_delta int NOT NULL,
 used_delta int NOT NULL,
 actor varchar(60) NOT NULL,
 note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(pass_id) REFERENCES club_pass(id),
 FOREIGN KEY(reservation_id) REFERENCES reservation(id)
);

CREATE TABLE cash_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 pass_id bigint NOT NULL,
 original_id bigint NULL,
 kind varchar(20) NOT NULL,
 reference varchar(160) NOT NULL,
 amount decimal(18,2) NOT NULL,
 note varchar(1000) NOT NULL,
 actor varchar(60) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(pass_id) REFERENCES club_pass(id),
 FOREIGN KEY(original_id) REFERENCES cash_entry(id),
 UNIQUE(reference),
 CHECK(amount > 0 AND amount <= 999999.99)
);

CREATE TABLE club_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 object_type varchar(30) NOT NULL,
 object_id bigint NOT NULL,
 action varchar(50) NOT NULL,
 actor varchar(60) NOT NULL,
 note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 INDEX ix_event_object(object_type,object_id)
);

CREATE TABLE check_token (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 member_id bigint NOT NULL,
 department_id bigint NOT NULL,
 token_hash varchar(64) NOT NULL,
 created_at timestamp(6) NOT NULL,
 expires_at timestamp(6) NOT NULL,
 used_at timestamp(6) NULL,
 FOREIGN KEY(member_id) REFERENCES member(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 UNIQUE(token_hash),
 INDEX ix_token_expiry(expires_at)
);

CREATE TABLE entry_visit (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 department_id bigint NOT NULL,
 member_id bigint NOT NULL,
 pass_id bigint NOT NULL,
 visit_date date NOT NULL,
 created_at timestamp(6) NOT NULL,
 actor varchar(60) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(member_id) REFERENCES member(id),
 FOREIGN KEY(pass_id) REFERENCES club_pass(id),
 UNIQUE(member_id,visit_date)
);
