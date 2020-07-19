CREATE TABLE person (
    id bigint NOT NULL,
    email text NOT NULL,
    full_name text NOT NULL
);

ALTER TABLE person OWNER TO postgres;

CREATE SEQUENCE person_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE person_id_seq OWNER TO postgres;

ALTER SEQUENCE person_id_seq OWNED BY person.id;

CREATE TABLE reservation (
    id text NOT NULL,
    duration daterange NOT NULL,
    person_id bigint,
    EXCLUDE USING gist (duration WITH &&)
);

ALTER TABLE reservation OWNER TO postgres;

ALTER TABLE ONLY person ALTER COLUMN id SET DEFAULT nextval('person_id_seq'::regclass);

ALTER TABLE ONLY person
    ADD CONSTRAINT person_pkey PRIMARY KEY (id);

ALTER TABLE ONLY reservation
    ADD CONSTRAINT reservation_pkey PRIMARY KEY (id);

