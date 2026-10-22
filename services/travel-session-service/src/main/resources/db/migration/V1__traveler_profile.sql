-- One row per traveler: profile and preferences. Okta owns identity and credentials, so this table never holds a
-- password, an email address, or the raw Okta subject; rows are keyed by the subject's SHA-256, the same owner key
-- the synced travel plan uses.
create table traveler_profile (
    owner_key    char(64)    primary key,
    display_name varchar(60),
    home_city    varchar(80),
    interests    text[]      not null default '{}',
    currency     char(3),
    travel_mode  varchar(10) not null default 'walk' check (travel_mode in ('walk', 'transit', 'drive')),
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    last_seen_at timestamptz not null
);
