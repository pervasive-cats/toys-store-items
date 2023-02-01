CREATE TABLE IF NOT EXISTS public.item_categories
(
    id bigserial NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(100) NOT NULL,
    CONSTRAINT item_categories_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.catalog_items
(
    id bigint NOT NULL,
    category bigint NOT NULL,
    store bigint NOT NULL,
    amount numeric(6,2) NOT NULL,
    currency character varying(3) NOT NULL,
    is_lifted boolean NOT NULL DEFAULT false,
    CONSTRAINT catalog_items_pkey PRIMARY KEY (id, store)
);