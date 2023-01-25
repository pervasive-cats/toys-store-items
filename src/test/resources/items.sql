CREATE TABLE IF NOT EXISTS public.item_categories
(
    id serial NOT NULL,
    name character varying(100) COLLATE pg_catalog."default" NOT NULL,
    description character varying(100) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT item_categories_pkey PRIMARY KEY (id)
);


