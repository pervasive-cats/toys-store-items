CREATE TABLE public.catalogitems
(
    id integer NOT NULL,
    category integer NOT NULL,
    store integer NOT NULL,
    price integer NOT NULL
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

ALTER TABLE ONLY public.catalogitems ADD CONSTRAINT catalogitems_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.itemcategories ADD CONSTRAINT itemcategories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.items ADD CONSTRAINT items_pkey PRIMARY KEY (id);