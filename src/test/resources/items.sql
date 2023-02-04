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

CREATE TYPE public.item_status AS ENUM
(
  'in_place',
  'in_cart',
  'returned'
);

CREATE TABLE IF NOT EXISTS public.items
(
    id bigserial NOT NULL,
    catalog_item_id bigint NOT NULL,
    customer character varying(100) NOT NULL,
    store bigint NOT NULL,
    is_returned item_status NOT NULL DEFAULT 'in_place',
    CONSTRAINT items_pkey PRIMARY KEY (id)
);

INSERT INTO public.catalog_items(
  id, category, store, amount, currency, is_lifted)
  VALUES ('4', '13', '15', '19.99', 'EUR', false);

INSERT INTO public.items(
  id, catalog_item_id, customer, store, is_returned)
  VALUES ('123', '345', 'elena@gmail.com', '614', 'in_cart');