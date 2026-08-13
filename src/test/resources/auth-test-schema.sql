DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS organization;

CREATE TABLE organization (
    organization_id NUMBER PRIMARY KEY,
    organization_name VARCHAR2(200) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE app_user (
    user_id NUMBER PRIMARY KEY,
    organization_id NUMBER NOT NULL,
    login_id VARCHAR2(100) NOT NULL,
    password_hash VARCHAR2(255) NOT NULL,
    user_name VARCHAR2(100) NOT NULL,
    email VARCHAR2(255),
    active_yn CHAR(1) DEFAULT 'Y' NOT NULL,
    last_login_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by NUMBER NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE app_role (
    role_id NUMBER PRIMARY KEY,
    role_code VARCHAR2(50) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE user_role (
    user_role_id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    role_id NUMBER NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);
