create table if not exists auth_users (
    id bigserial primary key,
    email varchar(255) not null unique,
    full_name varchar(255) not null,
    password_hash varchar(255) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists auth_tokens (
    token varchar(128) primary key,
    user_id bigint not null references auth_users(id) on delete cascade,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp
);
