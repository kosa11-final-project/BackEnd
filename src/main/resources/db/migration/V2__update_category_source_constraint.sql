ALTER TABLE category
    DROP CONSTRAINT ck_category_source;

ALTER TABLE category
    ADD CONSTRAINT ck_category_source
        CHECK (source IN ('HI', 'GREETING', 'NAVER', 'INTERNAL'));