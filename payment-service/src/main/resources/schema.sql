create table if not exists payment_records (
    session_id varchar(255) primary key,
    customer_id varchar(255) not null,
    cart_id varchar(255) not null,
    amount_total numeric(12, 2) not null,
    stripe_status varchar(64) not null,
    created_at timestamp not null,
    finalized_at timestamp null
);
