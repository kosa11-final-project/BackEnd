CREATE TABLE app_user (
    user_id    NUMBER        NOT NULL,
    login_id  VARCHAR2(100) NOT NULL,
    is_deleted NUMBER(1)    DEFAULT 0 NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id),
    CONSTRAINT uq_app_user_login_id UNIQUE (login_id)
);

INSERT INTO app_user (user_id, login_id, is_deleted)
VALUES (1, '__system__', 0);
