CREATE TABLE IF NOT EXISTS public.item_categories
(
    id serial NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(100) NOT NULL,
    CONSTRAINT item_categories_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.catalog_items
(
    id serial NOT NULL DEFAULT,
    category integer NOT NULL,
    store integer NOT NULL,
    price money NOT NULL,
    CONSTRAINT catalog_items_pkey PRIMARY KEY (id)
);