CREATE TABLE public.itemcategories
(
    id integer NOT NULL,
    name character varying(100)[]  NOT NULL,
    description character varying(100)[] NOT NULL
);

CREATE TABLE public.itemcategories
(
    id integer NOT NULL,
    name character varying(100)[] NOT NULL,
    description character varying(100)[] NOT NULL
);


CREATE TABLE public.items
(
    id integer NOT NULL,
    kind integer NOT NULL
);

ALTER TABLE ONLY public.itemcategories ADD CONSTRAINT itemcategories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.itemcategories ADD CONSTRAINT itemcategories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.items ADD CONSTRAINT items_pkey PRIMARY KEY (id);