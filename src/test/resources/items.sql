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
    count bigint NOT NULL DEFAULT 0,
    CONSTRAINT catalog_items_pkey PRIMARY KEY (id, store)
);

CREATE TYPE public.item_status AS ENUM
(
  'in_place',
  'in_cart',
  'returned'
);

CREATE TABLE IF NOT EXISTS public.items
(
    id bigint NOT NULL,
    catalog_item_id bigint NOT NULL,
    customer character varying(100),
    store bigint NOT NULL,
    status item_status NOT NULL DEFAULT 'in_place',
    CONSTRAINT items_pkey PRIMARY KEY (id)
);